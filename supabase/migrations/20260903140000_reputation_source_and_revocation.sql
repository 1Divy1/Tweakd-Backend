-- Reputation history — link each entry to the thing that earned it, and let an entry be revoked.
--
-- ===========================================================================
-- Part 1: why the source link is not a foreign key to car_events
-- ===========================================================================
--   * An FK would only constrain DELETEs, so it would not stand in the way of reshaping the
--     events feature — but deletes are exactly the problem. `deleteEvent` and `deleteEventAsAdmin`
--     both exist. ON DELETE RESTRICT would make an event undeletable the moment someone earned
--     points at it; ON DELETE CASCADE would erase reputation people actually earned, which is the
--     one thing this table exists to prevent.
--   * Sources are polymorphic. Of the nine seeded reasons, four point at car_events, three at
--     contests (no table yet), first_mod at car_modifications, and annual_member_anniversary at
--     nothing at all. A real FK means one more nullable column and one more migration per
--     feature, forever.
--
-- So the link is generic and unenforced, and the display label is *snapshotted* at award time.
-- That keeps the reputation module reading only its own tables — no join, no cross-module call, no
-- dependency on the current shape of events — and a history entry stays readable forever even if
-- the event behind it is renamed or deleted. The price is that a later rename does not propagate;
-- for an append-only ledger, showing what a thing was called when the points were earned is the
-- more honest answer anyway.
--
-- ===========================================================================
-- Part 2: revocation
-- ===========================================================================
-- Some awards have to come back. An event is cancelled after it finished, a contest placement is
-- corrected, a moderation penalty is appealed. Revoking stamps `revoked_at` on the original row
-- and subtracts exactly the delta that row applied (`score_gain`, which is already the clamped
-- value) — so a revocation is precisely symmetric to its award, even for a penalty that had hit
-- the zero floor.
--
-- The row is kept, not deleted, but it is NOT public. Revoked entries are filtered out of the
-- public timeline entirely: showing "reverted" to strangers would turn the feature into a shaming
-- mechanic. The owner still sees them, because a score that silently drops 50 points with no
-- explanation is worse.
--
-- Note the unique index below is partial on `revoked_at is null`. That is deliberate and load-
-- bearing: it means a revoked award can be earned again later (an event that gets un-cancelled,
-- a corrected contest result), while live awards still cannot be double-paid.

begin;

-- ---------------------------------------------------------------------------
-- 1. The source link
-- ---------------------------------------------------------------------------

alter table public.reputation_score_history
    add column if not exists source_type  text,
    add column if not exists source_id    uuid,
    add column if not exists source_label text;

comment on column public.reputation_score_history.source_type  is 'What kind of thing earned this, e.g. ''car_event''. NULL for entries with no source (anniversaries).';
comment on column public.reputation_score_history.source_id    is 'The source row''s id. Deliberately NOT a foreign key - see the migration header. Used for deep-linking.';
comment on column public.reputation_score_history.source_label is 'The source''s display name as it was at award time. Snapshotted so the entry survives the source being renamed or deleted.';

-- Half a link is worse than none: it would deep-link nowhere and dedupe nothing.
alter table public.reputation_score_history
    add constraint reputation_history_source_paired check (
        (source_type is null and source_id is null)
           or (source_type is not null and source_id is not null)
    );

-- Typos here silently break deep-linking, and the set only grows a value when a feature ships,
-- so it is cheap to keep honest. Extend with drop constraint / add constraint.
alter table public.reputation_score_history
    add constraint reputation_history_source_type_valid check (
        source_type is null or source_type in (
            'car_event', 'contest', 'car', 'car_modification',
            'forum_thread', 'forum_reply', 'marketplace_listing', 'review'
        )
    );

-- ---------------------------------------------------------------------------
-- 2. Revocation
-- ---------------------------------------------------------------------------

alter table public.reputation_score_history
    add column if not exists revoked_at     timestamptz,
    add column if not exists revoked_reason text;

comment on column public.reputation_score_history.revoked_at     is 'When this award was taken back. NULL = live. Revoked entries are hidden from the public timeline and excluded from the score.';
comment on column public.reputation_score_history.revoked_reason is 'Why it was taken back, e.g. ''Event was cancelled''. Shown to the owner only.';

-- A reason without a timestamp would be a revocation nobody applied.
alter table public.reputation_score_history
    add constraint reputation_history_revocation_paired check (
        revoked_reason is null or revoked_at is not null
    );

-- ---------------------------------------------------------------------------
-- 3. Idempotency
-- ---------------------------------------------------------------------------
-- This is what makes "10 pts for attending event Y" un-double-payable, at the database rather
-- than in whichever caller remembered to guard. A retried listener, a double-tapped check-in and
-- a replayed webhook all collapse onto the same row.
--
-- Partial on two conditions:
--   * source_id is not null  — sourceless entries (annual_member_anniversary) are legitimately
--                              repeatable and must not collide with each other;
--   * revoked_at is null     — a revoked award is no longer live, so the same thing can be earned
--                              again without tripping over the entry that was taken back.

create unique index if not exists reputation_score_history_source_uq
    on public.reputation_score_history (user_id, reason, source_type, source_id)
    where source_id is not null and revoked_at is null;

-- ---------------------------------------------------------------------------
-- 4. Read index
-- ---------------------------------------------------------------------------
-- The public timeline pages over live rows only, so the partial index matching that predicate
-- keeps those pages index seeks. The full index from the previous migration still serves the
-- owner's own timeline, which includes revoked entries.

create index if not exists reputation_score_history_user_live_idx
    on public.reputation_score_history (user_id, created_at desc, id desc)
    where revoked_at is null;

commit;
