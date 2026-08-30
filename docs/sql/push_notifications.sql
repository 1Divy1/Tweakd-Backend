-- ============================================================================
-- Push notifications — FCM device token registry
-- Feature branch: push-notifications
--
-- Single source of truth for the user <-> device-token relationship, shared by:
--   * the Spring backend  (sends push for the 20 non-dm notification types)
--   * a Supabase edge fn  (sends push for type = 'dm')
--
-- No changes to public.notifications are required: Spring dispatches from an
-- in-process application event, and the edge function is driven by its own
-- trigger, so there is no delivery-state column to maintain here.
-- ============================================================================

begin;

create table public.user_devices_firebase_token (
    id          uuid        primary key default gen_random_uuid(),
    user_id     uuid        not null references public.profiles(id) on delete cascade,
    token       text        not null,
    platform    text        not null,
    app_version text,
    locale      text,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),

    -- UNIQUE on token ALONE (not on (user_id, token)). This is the pivot that
    -- makes device handoff work: re-registering a token that already exists
    -- reassigns it to the new user via ON CONFLICT (token) DO UPDATE, so the
    -- previous account immediately stops receiving that device's pushes.
    constraint user_devices_firebase_token_token_key unique (token),

    constraint user_devices_firebase_token_platform_chk
        check (platform in ('ios', 'android')),

    -- FCM registration tokens sit around 140-200 chars. Bounded so a buggy or
    -- hostile client cannot write unbounded blobs into the table.
    constraint user_devices_firebase_token_token_chk
        check (length(token) between 32 and 512),

    constraint user_devices_firebase_token_app_version_chk
        check (app_version is null or length(app_version) between 1 and 32),

    -- BCP-47-ish: 'en', 'en-US', 'pt-BR', 'ro'.
    constraint user_devices_firebase_token_locale_chk
        check (locale is null or locale ~ '^[a-zA-Z]{2,3}([-_][a-zA-Z0-9]{2,8}){0,2}$')
);

comment on table public.user_devices_firebase_token is
    'FCM registration tokens, one row per device. Single source of truth shared by the Spring '
    'backend (all notification types except dm) and the Supabase edge function (dm). token is '
    'UNIQUE on its own, not (user_id, token): re-registering an existing token reassigns it to the '
    'calling user, which is how device handoff between accounts works. Never exposed over the REST '
    'API - there is no list-devices endpoint and no response ever contains a token.';

comment on column public.user_devices_firebase_token.token is
    'FCM registration token. A device-addressable secret: anyone holding it can push arbitrary '
    'notifications to that device. RLS-denied and revoked from anon/authenticated so no Supabase '
    'client can enumerate tokens.';

comment on column public.user_devices_firebase_token.updated_at is
    'Bumped on every re-registration by the touch trigger below. Doubles as the staleness '
    'watermark: FCM garbage-collects registrations after 270 days of inactivity.';

-- --------------------------------------------------------------------------
-- Indexes
-- --------------------------------------------------------------------------

-- Fan-out lookup: "every device belonging to these recipients".
create index user_devices_firebase_token_user_id_idx
    on public.user_devices_firebase_token (user_id);

-- Staleness pruning and the per-user device-cap eviction (oldest first).
create index user_devices_firebase_token_updated_at_idx
    on public.user_devices_firebase_token (updated_at);

-- --------------------------------------------------------------------------
-- updated_at maintenance
--
-- A trigger rather than application code, so the edge function (or any future
-- writer) cannot forget to bump it. search_path is pinned even though this is
-- not SECURITY DEFINER.
-- --------------------------------------------------------------------------

create or replace function public.user_devices_firebase_token_touch()
returns trigger
language plpgsql
set search_path to ''
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

create trigger user_devices_firebase_token_touch_updated_at
    before update on public.user_devices_firebase_token
    for each row
    execute function public.user_devices_firebase_token_touch();

-- --------------------------------------------------------------------------
-- Lockdown
--
-- RLS is enabled with DELIBERATELY ZERO POLICIES => deny-all for the anon and
-- authenticated roles. Both legitimate readers bypass RLS:
--   * the Spring backend connects as postgres (the table owner)
--   * the edge function uses service_role
-- so neither needs a policy. Any Flutter client holding the anon key is denied
-- outright and cannot enumerate another user's device tokens through PostgREST.
--
-- The REVOKE is belt-and-braces: Supabase's default privileges grant SELECT etc.
-- to anon/authenticated on new tables in the public schema, so the grants are
-- stripped explicitly rather than relying on RLS alone.
-- --------------------------------------------------------------------------

alter table public.user_devices_firebase_token enable row level security;

revoke all on public.user_devices_firebase_token from anon, authenticated;

commit;

-- ============================================================================
-- Post-apply verification (run separately, expect the noted results)
-- ============================================================================
--
--   -- 1. RLS on, and no policies exist:
--   select relrowsecurity from pg_class
--    where oid = 'public.user_devices_firebase_token'::regclass;          -- => true
--   select count(*) from pg_policies
--    where tablename = 'user_devices_firebase_token';                     -- => 0
--
--   -- 2. anon and authenticated hold no privileges:
--   select grantee, privilege_type from information_schema.role_table_grants
--    where table_name = 'user_devices_firebase_token'
--      and grantee in ('anon', 'authenticated');                          -- => 0 rows
--
--   -- 3. Then re-run ./scripts/dump-schema.sh so the Testcontainers schema
--   --    picks the new table up, or the JPA entity will fail ddl-auto: validate.
