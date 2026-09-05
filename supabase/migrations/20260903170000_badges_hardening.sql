-- Badges — the constraints and the audit column the backend's award path relies on.
--
-- The two tables already existed when this was written:
--   * badges       (id text pk, title, description, unlocked_badge_url, locked_badge_url,
--                   is_available, created_at)  -- admin-managed lookup
--   * user_badges  (id uuid pk, user_id -> profiles, badge_id -> badges, created_at)
--
-- Nothing here reshapes them. Everything below is either load-bearing for correctness under
-- concurrency, an index the FK was missing, or an accountability record for a privileged write.
-- Cosmetic changes (display ordering, id format checks, a rarity column) were deliberately left
-- out — they'd be schema churn for no security, performance or cost gain.

begin;

-- ---------------------------------------------------------------------------
-- 1. One badge per user, enforced by the database
-- ---------------------------------------------------------------------------
-- THE important one. `BadgeService.award` is called from inside whatever transaction witnessed the
-- achievement, so it will be retried, replayed and double-tapped. Its pre-check (does this user
-- already hold this badge?) is a read followed by a write, which two concurrent awards both win.
--
-- This index is what actually makes the award exactly-once: the second insert fails at flush, its
-- transaction rolls back with the achievement it was awarding, and the retry finds the row and
-- short-circuits. Callers therefore need no dedup guard of their own — the same guarantee, and the
-- same reasoning, as reputation_score_history_source_uq.
--
-- Not partial, unlike reputation's: revoking a badge deletes the row rather than tombstoning it,
-- so there is nothing left behind to collide with a later re-award.
--
-- It doubles as the read index for "this user's badges", which is the only per-user query the
-- feature has. No separate (user_id, created_at) index is added: a user holds a handful of badges,
-- and sorting a handful in memory costs less than maintaining a second index on every award.
create unique index if not exists user_badges_user_badge_uq
    on public.user_badges (user_id, badge_id);

-- ---------------------------------------------------------------------------
-- 2. Index the foreign key
-- ---------------------------------------------------------------------------
-- Postgres does not index the referencing side of a foreign key for you. Without this, every
-- statement that has to check badges -> user_badges scans the whole table:
--   * retiring or deleting a badge (ON DELETE RESTRICT has to prove nobody holds it),
--   * renaming a badge id (ON UPDATE CASCADE has to rewrite every holder),
--   * the dashboard's "how many users hold this badge" count.
-- The unique index above cannot serve these — badge_id is its second column.
create index if not exists user_badges_badge_id_idx
    on public.user_badges (badge_id);

-- ---------------------------------------------------------------------------
-- 3. Who granted it
-- ---------------------------------------------------------------------------
-- Badges are handed out two ways: automatically by the backend when an achievement fires, and by
-- hand from the admin dashboard. The second is a staff member writing to another person's profile,
-- and an unattributed privileged write is one nobody can review afterwards.
--
-- Nullable, and NULL means "the system awarded it" — that is the normal case, so this costs a byte
-- of null-bitmap on ordinary rows and nothing else.
--
-- Deliberately NOT a foreign key, for the same reason reputation's source link isn't one: staff are
-- profile-less auth users living in admin_team_members, app users live in profiles, and there is no
-- single table to point at. An FK to auth.users would also make removing a staff member either
-- impossible or destructive to the badge rows they granted.
alter table public.user_badges
    add column if not exists granted_by uuid;

comment on column public.user_badges.granted_by is
    'The staff member who granted this by hand, from admin_team_members. NULL = awarded automatically by the backend. Not an FK: staff and app users live in different tables, and removing a staff member must not disturb the badges they granted.';

-- ---------------------------------------------------------------------------
-- 4. The url columns hold R2 keys, not URLs
-- ---------------------------------------------------------------------------
-- The backend prefixes these with the configured public URL of the `app-assets` bucket at read
-- time (StorageService.publicUrl), exactly as it does for avatars and post images. Storing a full
-- URL here would produce a doubled prefix that 404s in the app, and it would defeat the point of
-- storing keys: moving the bucket or its domain is a config change, never a data migration.
--
-- Worth a CHECK rather than a comment because the failure is silent — a broken image in the app,
-- not an error anyone sees in a log.
alter table public.badges
    add constraint badges_urls_are_keys check (
        unlocked_badge_url not like 'http%'
        and (locked_badge_url is null or locked_badge_url not like 'http%')
    );

comment on table  public.badges is
    'Admin-managed catalogue of unlockable badges. Retire a badge with is_available = false — never DELETE, or you orphan the user_badges rows that earned it (the FK is ON DELETE RESTRICT and will refuse anyway).';
comment on column public.badges.id is
    'Stable code, referenced by user_badges.badge_id and by the Badges constants in the backend. Renaming it cascades to holders.';
comment on column public.badges.unlocked_badge_url is
    'R2 object key of the earned artwork within the app-assets bucket, e.g. ''badges/pioneer/badge-unlocked.svg''. A key, not a URL — the backend builds the full URL.';
comment on column public.badges.locked_badge_url is
    'R2 object key of the not-yet-earned artwork. NULL = no locked variant; the client greys the unlocked one out itself.';
comment on column public.badges.is_available is
    'false = retired: cannot be awarded and is hidden from the catalogue, but stays readable on the profiles that already hold it.';

comment on table  public.user_badges is
    'Which badges a user has unlocked. One row per (user, badge) — enforced by user_badges_user_badge_uq, which is what makes awarding idempotent. Revoking deletes the row; there is no tombstone.';

-- ---------------------------------------------------------------------------
-- 5. Close the client's access to both tables
-- ---------------------------------------------------------------------------
-- RLS is already on with zero policies, so anon/authenticated could not read a row. But Supabase's
-- default grants left TRUNCATE, REFERENCES and TRIGGER on both tables, and TRUNCATE is not subject
-- to RLS — a privilege no client should hold on a table it is not even allowed to read.
--
-- Neither table is ever read through the Supabase client: badges reach the app through the
-- backend's /api/v1/badges endpoints, which is what lets it filter retired badges and build the R2
-- URLs. So this revoke costs nothing today. Same treatment already applied to
-- user_devices_firebase_token, for the same reason.
--
-- If the mobile app ever needs to read badges directly, grant SELECT back and add a read policy —
-- do not re-grant everything.
revoke all on public.badges       from anon, authenticated;
revoke all on public.user_badges  from anon, authenticated;

commit;
