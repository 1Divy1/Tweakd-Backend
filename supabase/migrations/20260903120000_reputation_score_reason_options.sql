-- Reputation score — fills out the reason catalogue, ties history to it, and adds the read index.
--
-- Context. Three things already existed:
--   * profiles.reputation_score            int4 NOT NULL DEFAULT 0
--   * reputation_score_history             (id, user_id, reason, score_gain, previous_score,
--                                           new_score, created_at)
--   * reputation_score_reason_options      (id, reason, created_at)  -- created empty as a stub
--
-- This migration grows the stub into the catalogue the backend reads (a reason has to carry what
-- it is worth, what it groups under, and whether it can be earned twice), seeds it, points
-- reputation_score_history.reason at it, and creates the index the history pagination needs.
--
-- The score itself is written by the Spring backend inside one transaction alongside the history
-- row (see the `reputation` module) — there is deliberately no trigger here, so the previous/new
-- pair recorded in history always matches what the service computed under a row lock.
--
-- NOTE: this file records the catalogue as it was actually applied. There is no `description`
-- column: `reason` already reads as a full sentence, so a second prose column was redundant.

begin;

-- ---------------------------------------------------------------------------
-- 1. Grow the reason catalogue
-- ---------------------------------------------------------------------------
-- `id`      is the code stored in reputation_score_history.reason
-- `reason`  is the display label (same role as app_language_options.language)

alter table public.reputation_score_reason_options
    add column if not exists points        integer,
    add column if not exists category      text,
    add column if not exists is_repeatable boolean not null default true,
    add column if not exists is_active     boolean not null default true;

comment on table  public.reputation_score_reason_options               is 'Reference data: every way a user can gain or lose reputation. `id` is the code stored in reputation_score_history.reason.';
comment on column public.reputation_score_reason_options.id            is 'Stable code, referenced by reputation_score_history.reason. Renaming it cascades to history.';
comment on column public.reputation_score_reason_options.reason        is 'Display label, e.g. "Attended a car event".';
comment on column public.reputation_score_reason_options.points        is 'Default score delta. Negative for penalties. Never zero.';
comment on column public.reputation_score_reason_options.is_repeatable is 'false = one-time achievement; the backend refuses a second award for the same user.';
comment on column public.reputation_score_reason_options.is_active     is 'Retire a reason by setting this false — never DELETE, or you orphan history.';

-- ---------------------------------------------------------------------------
-- 2. Seed it
-- ---------------------------------------------------------------------------
-- Every reason is repeatable. Per-occurrence double-awarding is prevented instead by the
-- (user_id, reason, source_type, source_id) unique index added in the source-link migration —
-- so "attended event Y" pays once for event Y, but "attended an event" stays earnable forever.

insert into public.reputation_score_reason_options (id, reason, points, category, is_repeatable) values
    -- events
    ('car_event_organized',       'Organized a car event',                                            50,  'events',     true),
    ('event_car_showcased',       'Showcased a car at an event',                                       20,  'events',     true),
    ('event_attended',            'Attended a car event',                                             10,  'events',     true),
    -- contests
    ('contest_first_place',       'Won the first place',                                               50,  'contests',   true),
    ('contest_second_place',      'Won the second place',                                              40,  'contests',   true),
    ('contest_third_place',       'Won the third place',                                               30,  'contests',   true),
    -- garage
    ('first_mod',                 'Added the first mod on your car',                                   20,  'garage',     true),
    -- trust
    ('annual_member_anniversary', 'Yearly award for being in the community',                          100,  'trust',      true),
    -- moderation (penalties — negative points)
    ('event_no_show',             'No-show at an event they joined without announcing the organizers', -50, 'moderation', true)
on conflict (id) do nothing;

-- ---------------------------------------------------------------------------
-- 3. Lock the new columns down now that every row has a value
-- ---------------------------------------------------------------------------
-- Added nullable above so the ALTER succeeds on a populated table; tightened here, after the seed.
-- The backend maps both as non-null, so a NULL would fail Hibernate's startup validation.

alter table public.reputation_score_reason_options
    alter column points   set not null,
    alter column category set not null;

alter table public.reputation_score_reason_options
    add constraint reputation_reason_points_nonzero check (points <> 0),
    add constraint reputation_reason_category_valid check (
        category in ('events', 'contests', 'community', 'garage', 'trust', 'marketplace', 'moderation')
    );

-- ---------------------------------------------------------------------------
-- 4. Tie history to the catalogue
-- ---------------------------------------------------------------------------
-- Safe as-is: reputation_score_history was empty. If you ever re-run this against a populated
-- table, seed every referenced code above first or the constraint will fail.
--
-- ON DELETE RESTRICT is the point: retire a reason with is_active = false, never by deleting it,
-- or the history rows that earned it lose their meaning.

alter table public.reputation_score_history
    add constraint reputation_score_history_reason_fkey
    foreign key (reason) references public.reputation_score_reason_options (id)
    on update cascade on delete restrict;

-- ---------------------------------------------------------------------------
-- 5. The index the backend's keyset pagination reads on
-- ---------------------------------------------------------------------------
-- Serves "one user's history, newest first" — (user_id, created_at desc, id desc) is exactly the
-- ORDER BY the paged reads use, so pages are index seeks rather than a sort per request.

create index if not exists reputation_score_history_user_created_idx
    on public.reputation_score_history (user_id, created_at desc, id desc);

commit;
