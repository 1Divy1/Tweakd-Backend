// Fires one FCM push per device when a `dm` row lands in public.notifications.
//
// Invoked only by the `notifications_dm_push` trigger (pg_net). Authentication
// is the x-webhook-secret header, compared in constant time; verify_jwt stays on
// so Supabase's gateway drops unauthenticated internet traffic before this runs.
//
// This function reads no table directly. Its entire data surface is the
// dm_push_bundle RPC, which refuses anything that is not type = 'dm' and
// applies the recipient's dms_enabled and blocked-accounts rules.

import { createClient } from "jsr:@supabase/supabase-js@2";

const SA = JSON.parse(Deno.env.get("FIREBASE_SERVICE_ACCOUNT")!);
const WEBHOOK_SECRET = Deno.env.get("DM_PUSH_WEBHOOK_SECRET")!;
const FCM_URL = `https://fcm.googleapis.com/v1/projects/${SA.project_id}/messages:send`;
const ANDROID_CHANNEL_ID = "tweakd_default";

const db = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

/** Length-safe, branch-free compare so a wrong secret leaks no timing signal. */
function safeEqual(a: string, b: string): boolean {
  const x = new TextEncoder().encode(a);
  const y = new TextEncoder().encode(b);
  if (x.length !== y.length) return false;
  let diff = 0;
  for (let i = 0; i < x.length; i++) diff |= x[i] ^ y[i];
  return diff === 0;
}

const b64url = (bytes: Uint8Array) =>
  btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const b64urlText = (s: string) => b64url(new TextEncoder().encode(s));

// Module scope, so a warm isolate reuses the token instead of re-signing.
let cached: { token: string; expiresAt: number } | null = null;

/** Google OAuth2 via a self-signed service-account JWT. No SDK, fast cold start. */
async function accessToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cached && cached.expiresAt > now + 60) return cached.token;

  const pem = SA.private_key
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const key = await crypto.subtle.importKey(
    "pkcs8",
    Uint8Array.from(atob(pem), (c) => c.charCodeAt(0)),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );

  const claim = `${b64urlText(JSON.stringify({ alg: "RS256", typ: "JWT" }))}.` +
    b64urlText(JSON.stringify({
      iss: SA.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    }));
  const sig = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(claim),
  );

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${claim}.${b64url(new Uint8Array(sig))}`,
    }),
  });
  if (!res.ok) throw new Error(`token exchange failed: ${res.status}`);

  const json = await res.json();
  cached = { token: json.access_token, expiresAt: now + json.expires_in };
  return cached.token;
}

// Same set the Spring sender prunes on, so both paths agree on "dead token".
const DEAD = new Set(["UNREGISTERED", "INVALID_ARGUMENT", "SENDER_ID_MISMATCH"]);

/** Returns the token if FCM says it is permanently dead, else null. */
async function sendOne(
  token: string,
  bundle: Record<string, any>,
  bearer: string,
): Promise<string | null> {
  // Every FCM data value must be a string.
  const data: Record<string, string> = {
    type: "dm",
    notification_id: String(bundle.notification_id),
    unread_count: String(bundle.unread_count),
  };
  for (const [k, v] of Object.entries(bundle.payload ?? {})) {
    if (v !== null && v !== undefined) data[k] = String(v);
  }

  const res = await fetch(FCM_URL, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${bearer}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      message: {
        token,
        notification: { title: bundle.title, body: bundle.body ?? "" },
        data,
        android: {
          priority: "HIGH",
          notification: { channel_id: ANDROID_CHANNEL_ID },
        },
        apns: {
          payload: {
            aps: { badge: Number(bundle.unread_count), sound: "default" },
          },
        },
      },
    }),
  });

  if (res.ok) return null;

  const err = await res.json().catch(() => null);
  const code = err?.error?.details?.find(
    (d: any) => d["@type"]?.endsWith("FcmError"),
  )?.errorCode;
  // Only ever log a masked prefix — a full token is a push credential.
  console.warn(`fcm ${res.status} ${code ?? "?"} for ${token.slice(0, 8)}...`);
  return DEAD.has(code) ? token : null;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") {
    return new Response("method not allowed", { status: 405 });
  }
  if (!safeEqual(req.headers.get("x-webhook-secret") ?? "", WEBHOOK_SECRET)) {
    return new Response("unauthorized", { status: 401 });
  }

  let id: string;
  try {
    id = (await req.json()).notification_id;
  } catch {
    return new Response("bad request", { status: 400 });
  }
  if (typeof id !== "string") {
    return new Response("bad request", { status: 400 });
  }

  const { data: bundle, error } = await db.rpc("dm_push_bundle", {
    p_notification_id: id,
  });
  if (error) {
    console.error("dm_push_bundle failed", error.message);
    return new Response("error", { status: 500 });
  }
  // null = not a DM, muted, or blocked. Empty tokens = no devices registered.
  if (!bundle || !bundle.tokens?.length) {
    return new Response("skipped", { status: 200 });
  }

  const bearer = await accessToken();
  const results = await Promise.allSettled(
    bundle.tokens.map((t: string) => sendOne(t, bundle, bearer)),
  );

  const dead = results
    .filter((r) => r.status === "fulfilled" && r.value)
    .map((r) => (r as PromiseFulfilledResult<string>).value);

  if (dead.length) {
    await db.from("user_devices_firebase_token").delete().in("token", dead);
  }

  return new Response(
    JSON.stringify({ sent: bundle.tokens.length, pruned: dead.length }),
    { status: 200, headers: { "Content-Type": "application/json" } },
  );
});
