# DM push — Supabase edge function guide

Companion to `PUSH_NOTIFICATIONS_PROGRESS.md`.

> ## ✅ APPLIED 2026-08-30 — do not re-run Parts 3–7
>
> All of this is live on project `fybgmaigzidhbmhbgfhu`, as migrations
> `dm_send_message_writes_notification`, `dm_push_bundle_rpc`, `enable_pg_net`,
> `dm_push_notify_trigger`, plus edge function `dm-push` v1 (`verify_jwt: true`). The deployed
> function source lives at [`supabase/functions/dm-push/index.ts`](../supabase/functions/dm-push/index.ts)
> — **edit that file**, not the listing in Part 6, and redeploy from it.
>
> **One deliberate change from Part 5 below.** Part 5 stores three values in Vault (function URL,
> anon key, webhook secret), costing three pgsodium decrypts on every DM. Only the webhook secret is
> secret — the URL and the anon key are public by design, since the anon key ships inside the Flutter
> app. So the URL and anon key are inlined in `dm_push_notify()` and Vault holds exactly one entry,
> `dm_push_webhook_secret`: **one decrypt per DM instead of three.** Read Part 5 for the reasoning,
> not for the statements to run.
>
> Verification results are recorded in `PUSH_NOTIFICATIONS_PROGRESS.md`. The only thing not yet
> proven end-to-end is a real delivered push, because no device has registered a token yet.

The rest of this document is kept as the rationale for each decision. Ordered so each step is
testable before the next.

---

## Part 1 — Updated endpoint contracts (Flutter changes)

Two endpoints, both new. The only *breaking* difference from what you had planned is the DELETE.

### `POST /api/v1/notifications/devices` → `200`, empty body

```json
{
  "token": "fcm-token…",
  "platform": "ios",
  "app_version": "1.0.0+1",
  "locale": "en"
}
```

| Field | Required | Rules |
|---|---|---|
| `token` | yes | 32–512 chars |
| `platform` | yes | exactly `ios` or `android` |
| `app_version` | no | ≤ 32 chars |
| `locale` | no | `en`, `en-US`, `en_US`… (`^[a-zA-Z]{2,3}([-_][a-zA-Z0-9]{2,8}){0,2}$`) |

Keys are **snake_case** — the backend runs a global `SNAKE_CASE` Jackson strategy, so `appVersion`
on the wire must be `app_version`. Violations are `400`, never a 500.

Idempotent upsert keyed on the token. Re-posting the same token from a different account **moves**
the device to that account — this is the handoff you asked for, and it is why logout→login on a
shared phone does not leave the previous owner receiving the new owner's pushes. Call it on every
app start and on every `onTokenRefresh`.

The account is always the JWT subject. There is no `user_id` field, and adding one would do nothing.

### `DELETE /api/v1/notifications/devices` → `204`, empty body

```json
{ "token": "fcm-token…" }
```

**This is the change.** The token is in the **body, not the path** — `DELETE /devices/{token}`
writes the token verbatim into Cloud Run access logs, any proxy in front, and browser/CDN history,
and an FCM token is a device-addressable secret: whoever holds it can push arbitrary notifications
to that phone.

Dart's `package:http` will not send a body on `delete()`. Use `Request` directly, or `dio`:

```dart
// package:http
final req = http.Request('DELETE', Uri.parse('$baseUrl/api/v1/notifications/devices'))
  ..headers.addAll({
    'Authorization': 'Bearer $accessToken',
    'Content-Type': 'application/json',
  })
  ..body = jsonEncode({'token': token});
final res = await http.Client().send(req);

// dio
await dio.delete('/api/v1/notifications/devices', data: {'token': token});
```

Always `204`, whether or not a row matched — the endpoint deliberately is not an oracle for whether
a given token exists. Deletion is scoped `where token = ? and user_id = ?`, so replaying someone
else's token cannot cut off their push.

Call it on logout, **before** clearing the session — it needs the JWT.

### What arrives on the device

Identical shape from both senders (Spring and your edge function):

```json
{
  "notification": { "title": "…", "body": "…" },
  "data": {
    "type": "post_like", "notification_id": "…", "unread_count": "7",
    "actor_id": "…", "actor_username": "…", "post_id": "…"
  },
  "android": { "notification": { "channel_id": "tweakd_default" } },
  "apns": { "payload": { "aps": { "badge": 7, "sound": "default" } } }
}
```

Every `data` value is a string, including `unread_count` and the boolean `car_tagged`. Nulls are
dropped rather than sent as `"null"`. The Android channel id is `tweakd_default` — it must exist in
the app or Android silently drops the notification on 8.0+.

---

## Part 2 — The gap you have to close first

I checked the live database. **`dm_send_message` never writes to `public.notifications`**, and there
is no `dm` row in the table today (only `post_like`, `post_comment`, `forum_*`, `feedback_*`).

So "an edge function fires when a row lands in `notifications`" has no source rows yet. Step 1 is
making the RPC write one.

I also found something that would have blocked you on day one, and it is the same class of problem
as RF-9:

```
service_role grants in public:
  profiles                     → SELECT, INSERT, UPDATE, DELETE
  user_devices_firebase_token  → SELECT, DELETE          (from my migration)
  notifications                → REFERENCES, TRIGGER, TRUNCATE   ← no SELECT
  notification_preferences     → REFERENCES, TRIGGER, TRUNCATE   ← no SELECT
  dm_messages / dm_conversations / dm_participant_state / blocked_accounts → same
```

**Your edge function cannot read `notifications`.** PostgREST would authenticate fine and return a
permission error (or, worse in some shapes, an empty set).

The tempting fix is `grant select on ... to service_role`. Don't. That posture is an asset, not a
bug — it means a leaked service_role key cannot read your users' DMs or notification history. Keep
it and go through a single `SECURITY DEFINER` RPC instead, which is Part 4.

`pg_net` is also **not installed** yet (Part 5).

---

## Part 3 — Amend `dm_send_message`

Add this immediately before the `return jsonb_build_object(...)` at the end. Everything else in the
function stays as it is.

```sql
  -- Push vehicle for the recipient. Same transaction as the message, so a failed send
  -- cannot leave a notification behind, and the AFTER INSERT trigger below only ever
  -- fires for a message that really committed.
  insert into public.notifications (id, user_id, type, title, body, payload, is_read, created_at)
  select
    gen_random_uuid(),
    p_recipient_id,
    'dm',
    coalesce(nullif(s.username, ''), 'Someone') || ' sent you a message',
    case when btrim(v_content) = '' then 'Shared a car' else left(v_content, 200) end,
    jsonb_build_object(
      'actor_id',        v_sender::text,
      'actor_username',  coalesce(nullif(s.username, ''), 'Someone'),
      'conversation_id', v_conversation_id::text,
      'message_id',      v_message_id::text
    ),
    false,
    v_now
  from public.profiles s
  where s.id = v_sender;
```

Notes:

- Payload ids are cast to `::text` on purpose — every `data` value FCM carries is a string, and this
  keeps the row identical to what Spring writes for the other 20 types.
- `left(v_content, 200)` matches the backend's `MAX_BODY_LENGTH`. FCM caps the whole payload at 4 KB
  and `content` is allowed up to 2000 chars, so the truncation is required, not cosmetic.
- The function is already `SECURITY DEFINER` with `search_path = ''`, so the fully-qualified
  `public.` prefixes are mandatory. Keep them.

**Settled (owner, 2026-08-29):** these rows appear in `GET /api/v1/notifications` alongside likes
and comments and count toward `unread-count` — "they are still notifications". That keeps the badge
one coherent number from one source, and needs no change on the Spring side: the feed query and
`countUnread` are type-agnostic already.

---

## Part 4 — One SECURITY DEFINER RPC, no new table grants

This is the whole data-access surface of the edge function. It takes a notification id and returns
the push bundle, having already applied every gate.

```sql
create or replace function public.dm_push_bundle(p_notification_id uuid)
returns jsonb
language plpgsql
security definer
set search_path to ''
as $$
declare
  v_n      public.notifications%rowtype;
  v_unread bigint;
  v_result jsonb;
begin
  select * into v_n from public.notifications where id = p_notification_id;

  -- Only DM rows are reachable through this path. Everything else belongs to the
  -- Spring dispatcher, and this is what makes a leaked webhook secret useless for
  -- re-sending arbitrary notifications.
  if not found or v_n.type <> 'dm' then
    return null;
  end if;

  -- Opted out of DM notifications. A missing preferences row means "all enabled".
  if exists (
    select 1 from public.notification_preferences p
     where p.profile_id = v_n.user_id and p.dms_enabled = false
  ) then
    return null;
  end if;

  -- Recipient blocked the sender: the row stays in the feed, the push does not go out.
  if v_n.payload ? 'actor_id' and exists (
    select 1 from public.blocked_accounts b
     where b.blocker_id = v_n.user_id
       and b.blocked_id = (v_n.payload ->> 'actor_id')::uuid
  ) then
    return null;
  end if;

  select count(*) into v_unread
    from public.notifications n
   where n.user_id = v_n.user_id and n.is_read = false;

  select jsonb_build_object(
           'notification_id', v_n.id,
           'title',           v_n.title,
           'body',            v_n.body,
           'payload',         coalesce(v_n.payload, '{}'::jsonb),
           'unread_count',    v_unread,
           'tokens',          coalesce(jsonb_agg(d.token), '[]'::jsonb))
    into v_result
    from public.user_devices_firebase_token d
   where d.user_id = v_n.user_id;

  return v_result;
end;
$$;

-- Postgres grants EXECUTE on new functions to PUBLIC by default. Without this revoke,
-- any logged-in user could call it with a guessed uuid and read someone's device tokens.
revoke all on function public.dm_push_bundle(uuid) from public, anon, authenticated;
grant execute on function public.dm_push_bundle(uuid) to service_role;
```

Why an RPC rather than grants:

- the edge function reads **no table directly** — it cannot enumerate tokens, cannot read anyone's
  notifications, cannot read DM content beyond the one preview it is about to push;
- the `type <> 'dm'` check is enforced server-side, so even a caller holding the webhook secret
  cannot use this to re-fire a `post_like` push or spoof one;
- all three gates (type, `dms_enabled`, block) live in one place instead of in TypeScript.

**Optional hardening, recommended:** with the RPC returning tokens, the edge function no longer needs
to read the table at all. Tighten RF-9 to delete-only:

```sql
revoke select on public.user_devices_firebase_token from service_role;   -- keeps DELETE for pruning
```

Spring connects as the table owner, not `service_role`, so this does not affect the backend.

---

## Part 5 — Secrets and the trigger

`pg_net` is not installed. Enable it:

```sql
create extension if not exists pg_net with schema extensions;
```

Generate a webhook secret locally (**not** in SQL — a `gen_random_bytes()` call would land in the
database logs):

```bash
openssl rand -hex 32
```

Store it, plus the function URL, in Vault:

```sql
select vault.create_secret('<the 64-hex secret>', 'dm_push_webhook_secret');
select vault.create_secret(
  'https://fybgmaigzidhbmhbgfhu.supabase.co/functions/v1/dm-push', 'dm_push_function_url');
select vault.create_secret('<your anon key>', 'dm_push_anon_key');
```

The **anon** key, not the service_role key. It only exists to satisfy the edge-function gateway's
`verify_jwt` check; it is already public in your Flutter app, so if the trigger definition ever leaks
it costs nothing. The real authentication is the `x-webhook-secret` header.

```sql
create or replace function public.dm_push_notify()
returns trigger
language plpgsql
security definer
set search_path to ''
as $$
declare
  v_url    text;
  v_secret text;
  v_anon   text;
begin
  select decrypted_secret into v_url    from vault.decrypted_secrets where name = 'dm_push_function_url';
  select decrypted_secret into v_secret from vault.decrypted_secrets where name = 'dm_push_webhook_secret';
  select decrypted_secret into v_anon   from vault.decrypted_secrets where name = 'dm_push_anon_key';

  -- Missing config must never fail a message send. No push is recoverable; a lost DM is not.
  if v_url is null or v_secret is null then
    return null;
  end if;

  perform net.http_post(
    url     := v_url,
    headers := jsonb_build_object(
                 'Content-Type',     'application/json',
                 'Authorization',    'Bearer ' || coalesce(v_anon, ''),
                 'x-webhook-secret', v_secret),
    -- Only the id. The function re-reads everything through dm_push_bundle, so nothing
    -- in this body is trusted and a replayed request cannot inject notification text.
    body    := jsonb_build_object('notification_id', new.id),
    timeout_milliseconds := 5000
  );
  return null;
end;
$$;

create trigger notifications_dm_push
after insert on public.notifications
for each row
when (new.type = 'dm')
execute function public.dm_push_notify();
```

The `when (new.type = 'dm')` clause is **RF-3**, closed structurally: the trigger cannot fire for the
20 types Spring already pushes, so no notification can go out twice. Do not drop it and filter inside
the function instead — that leaves a window where a code edit re-opens the double-push.

`net.http_post` is asynchronous and transactional: it queues the request, so it never adds latency to
`dm_send_message`, and a rolled-back send cancels the queued push.

---

## Part 6 — The edge function

`supabase/functions/dm-push/index.ts`. No npm dependencies — the OAuth2 assertion is ~30 lines of
Web Crypto, and a lean function means a fast cold start on a path a user is waiting on.

```ts
import { createClient } from "jsr:@supabase/supabase-js@2";

const SA = JSON.parse(Deno.env.get("FIREBASE_SERVICE_ACCOUNT")!);
const WEBHOOK_SECRET = Deno.env.get("DM_PUSH_WEBHOOK_SECRET")!;
const FCM_URL = `https://fcm.googleapis.com/v1/projects/${SA.project_id}/messages:send`;
const ANDROID_CHANNEL_ID = "tweakd_default";

const db = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

// ---------- auth ----------

function safeEqual(a: string, b: string): boolean {
  const x = new TextEncoder().encode(a);
  const y = new TextEncoder().encode(b);
  if (x.length !== y.length) return false;
  let diff = 0;
  for (let i = 0; i < x.length; i++) diff |= x[i] ^ y[i];
  return diff === 0;
}

// ---------- google oauth2 ----------

const b64url = (bytes: Uint8Array) =>
  btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const b64urlText = (s: string) => b64url(new TextEncoder().encode(s));

// Module scope: survives across invocations in a warm isolate, so most requests skip
// the token exchange entirely.
let cached: { token: string; expiresAt: number } | null = null;

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
    "RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(claim),
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

// ---------- fcm ----------

// The same classification the backend uses. Everything else (UNAVAILABLE, INTERNAL,
// QUOTA_EXCEEDED) is transient — a Firebase outage must never cost a user their device.
const DEAD = new Set(["UNREGISTERED", "INVALID_ARGUMENT", "SENDER_ID_MISMATCH"]);

async function sendOne(token: string, bundle: any, bearer: string): Promise<string | null> {
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
    headers: { Authorization: `Bearer ${bearer}`, "Content-Type": "application/json" },
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
          payload: { aps: { badge: Number(bundle.unread_count), sound: "default" } },
        },
      },
    }),
  });

  if (res.ok) return null;

  const err = await res.json().catch(() => null);
  const code = err?.error?.details?.find(
    (d: any) => d["@type"]?.endsWith("FcmError"),
  )?.errorCode;
  // Only ever log a prefix: a full token in the logs is a push credential.
  console.warn(`fcm ${res.status} ${code ?? "?"} for ${token.slice(0, 8)}…`);
  return DEAD.has(code) ? token : null;
}

// ---------- handler ----------

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("method not allowed", { status: 405 });
  if (!safeEqual(req.headers.get("x-webhook-secret") ?? "", WEBHOOK_SECRET)) {
    return new Response("unauthorized", { status: 401 });
  }

  let id: string;
  try {
    id = (await req.json()).notification_id;
  } catch {
    return new Response("bad request", { status: 400 });
  }
  if (typeof id !== "string") return new Response("bad request", { status: 400 });

  const { data: bundle, error } = await db.rpc("dm_push_bundle", { p_notification_id: id });
  if (error) {
    console.error("dm_push_bundle failed", error.message);
    return new Response("error", { status: 500 });
  }
  // null = not a dm, opted out, or blocked. All three are a normal no-op.
  if (!bundle || !bundle.tokens?.length) return new Response("skipped", { status: 200 });

  const bearer = await accessToken();
  // FCM v1 has no batch endpoint (Google retired it in June 2024), so it is one request
  // per token — bounded by the per-user cap of 10 devices.
  const results = await Promise.allSettled(
    bundle.tokens.map((t: string) => sendOne(t, bundle, bearer)),
  );

  const dead = results
    .filter((r) => r.status === "fulfilled" && r.value)
    .map((r) => (r as PromiseFulfilledResult<string>).value);

  if (dead.length) {
    await db.from("user_devices_firebase_token").delete().in("token", dead);
  }

  return new Response(JSON.stringify({ sent: bundle.tokens.length, pruned: dead.length }), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
});
```

### Deploy

```bash
supabase functions deploy dm-push --project-ref fybgmaigzidhbmhbgfhu
supabase secrets set --project-ref fybgmaigzidhbmhbgfhu \
  DM_PUSH_WEBHOOK_SECRET="<the same 64-hex secret you put in Vault>" \
  FIREBASE_SERVICE_ACCOUNT="$(cat path/to/serviceAccountKey.json)"
```

Keep `verify_jwt` at its default (**on**). It does not authenticate the caller for our purposes —
any logged-in user's JWT satisfies it — but it makes the gateway reject unauthenticated internet
traffic before your code runs. The `x-webhook-secret` check is the real gate.

Do not commit the service account JSON. If it is currently in your repo or Downloads, rotate it in
the Firebase console after this is working.

---

## Part 7 — Verify

```sql
-- 1. RPC in isolation, with a real dm notification id.
select public.dm_push_bundle('<notification uuid>');
-- expect: tokens[], title, body, unread_count. NULL means a gate rejected it.

-- 2. It must be unreachable as a normal user.
set local role authenticated;
select public.dm_push_bundle('<notification uuid>');   -- expect: permission denied
reset role;

-- 3. It must refuse a non-dm row.
select public.dm_push_bundle('<a post_like notification uuid>');  -- expect: NULL

-- 4. End to end: send a real DM from one account to another, then
select * from net._http_response order by created desc limit 1;   -- expect: 200
```

Then check the function logs in the dashboard. `{"sent":1,"pruned":0}` is the goal.

Negative checks worth doing once, since this is the security boundary:

- `curl -X POST <url> -H "Authorization: Bearer <anon>" -d '{"notification_id":"…"}'`
  with **no** `x-webhook-secret` → `401`.
- same, with the secret but a `post_like` notification id → `200 skipped`, no push.
- turn `dms_enabled` off for the recipient, send a DM → row is created, no push.

---

## Open items

- [ ] **Cloud Run service account needs FCM permission** (RF-8) — `roles/firebasemessaging.admin` on
      the runtime SA, or Spring silently degrades to `NoOpFcmSender`. Watch the startup logs for
      `Firebase push unavailable, falling back to no-op sender`.
- [ ] **Flutter DELETE change** — token moves to the request body (Part 1).
- [x] ~~Decide whether `dm` rows show in the in-app notifications feed~~ — yes, they do (Part 3).
