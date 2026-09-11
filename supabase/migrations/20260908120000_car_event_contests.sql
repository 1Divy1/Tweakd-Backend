-- Contests inside car events.
--
-- An organizer opens votes per category ("best exhaust", "best wheels", a custom one) inside a
-- meet. Owners of accepted cars ask to enter a contest; organizers accept or reject. Attendees
-- who RSVP'd `attending` vote once per contest and may change that vote while it is open. When a
-- contest closes — on its closing time, or early by an organizer — the top three are finalised,
-- paid reputation, given a badge, and told.
--
-- Everything here follows the car_event* posture: RLS enabled, no policies, no grants. The
-- backend is the only reader and writer. The single exception is the realtime.messages policy at
-- the end, which lets the app *read* the live board on a private per-event topic.
--
-- Counters (votes_count, entries_count, last_vote_at) are TRIGGER-OWNED, like attendees_count
-- and attending_cars_count on car_events: the backend never writes a count column.

begin;

-- ---------------------------------------------------------------------------
-- 1. Categories — the predefined list plus "custom". Reference data.
-- ---------------------------------------------------------------------------
create table public.car_event_contest_categories (
    id           text primary key,
    label        text not null,
    icon         text not null,
    sort_order   smallint not null,
    is_available boolean not null default true,
    created_at   timestamptz not null default now()
);

comment on table public.car_event_contest_categories is
    'The categories an organizer can pick when creating a contest inside an event. `custom` lets them type their own title. Retire one with is_available = false; never delete (contests reference it).';
comment on column public.car_event_contest_categories.icon is
    'Glyph key the app maps to a local icon: exhaust | wheels | paint | interior | loud | trophy. Unknown keys fall back to trophy.';

insert into public.car_event_contest_categories (id, label, icon, sort_order) values
    ('exhaust',  'Best exhaust system', 'exhaust',  10),
    ('wheels',   'Best wheels',         'wheels',   20),
    ('paint',    'Best paint / wrap',   'paint',    30),
    ('interior', 'Best interior',       'interior', 40),
    ('loudest',  'Loudest',             'loud',     50),
    ('custom',   'Custom category',     'trophy',   99);

-- ---------------------------------------------------------------------------
-- 2. Contests
-- ---------------------------------------------------------------------------
create table public.car_event_contests (
    id             uuid primary key default gen_random_uuid(),
    event_id       uuid not null references public.car_events(id) on update cascade on delete cascade,
    category_id    text not null references public.car_event_contest_categories(id) on update cascade on delete restrict,
    title          text not null,
    criteria       text,
    status         text not null default 'scheduled',
    opens_at       timestamptz not null,
    closes_at      timestamptz not null,
    finished_at    timestamptz,
    finished_early boolean not null default false,
    finished_by    uuid,
    votes_count    integer not null default 0,
    entries_count  integer not null default 0,
    created_by     uuid not null,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),
    constraint car_event_contests_title_len_ck     check (char_length(title) between 3 and 60),
    constraint car_event_contests_criteria_len_ck  check (criteria is null or char_length(criteria) <= 300),
    constraint car_event_contests_status_ck        check (status in ('scheduled', 'open', 'finished', 'canceled')),
    constraint car_event_contests_window_ck        check (closes_at > opens_at),
    constraint car_event_contests_finished_pair_ck check ((status = 'finished') = (finished_at is not null)),
    constraint car_event_contests_votes_count_ck   check (votes_count >= 0),
    constraint car_event_contests_entries_count_ck check (entries_count >= 0)
);

comment on table public.car_event_contests is
    'A vote that runs inside a car event. scheduled = published, entries open, voting locked; open = voting; finished = results final (top 3 awarded); canceled is reserved.';
comment on column public.car_event_contests.finished_by is
    'The organizer who finished it early. NULL = closed by the clock at closes_at. Not an FK: a departed organizer must not take the result with them.';
comment on column public.car_event_contests.created_by is
    'The organizer who created it. Not an FK for the same reason as finished_by; resolved to a profile on read, rendered nameless if gone.';
comment on column public.car_event_contests.votes_count is
    'Trigger-owned (trg_contest_vote_counts). Live total of votes cast.';
comment on column public.car_event_contests.entries_count is
    'Trigger-owned (trg_contest_entries_count). Number of accepted entries.';

create index car_event_contests_event_status_idx on public.car_event_contests (event_id, status);
create index car_event_contests_due_open_idx  on public.car_event_contests (opens_at)  where status = 'scheduled';
create index car_event_contests_due_close_idx on public.car_event_contests (closes_at) where status = 'open';

-- ---------------------------------------------------------------------------
-- 3. Entries — which cars are on the ballot. One row per (contest, car).
-- ---------------------------------------------------------------------------
create table public.car_event_contest_entries (
    contest_id        uuid not null references public.car_event_contests(id) on update cascade on delete cascade,
    car_id            uuid not null references public.cars(id) on update cascade on delete cascade,
    owner_id          uuid not null references public.profiles(id) on update cascade on delete cascade,
    status            text not null default 'pending',
    rejection_reason  text,
    requested_at      timestamptz not null default now(),
    decided_at        timestamptz,
    decided_by        uuid,
    votes_count       integer not null default 0,
    last_vote_at      timestamptz,
    final_rank        smallint,
    final_votes_count integer,
    primary key (contest_id, car_id),
    constraint car_event_contest_entries_status_ck      check (status in ('pending', 'accepted', 'rejected', 'withdrawn')),
    constraint car_event_contest_entries_reason_len_ck  check (rejection_reason is null or char_length(rejection_reason) <= 300),
    constraint car_event_contest_entries_reason_ck      check (status <> 'rejected' or rejection_reason is not null),
    constraint car_event_contest_entries_votes_count_ck check (votes_count >= 0),
    constraint car_event_contest_entries_final_rank_ck  check (final_rank is null or final_rank >= 1)
);

comment on table public.car_event_contest_entries is
    'A car asking to be judged in a contest. The owner requests; an organizer accepts or rejects. Only accepted rows are on the ballot.';
comment on column public.car_event_contest_entries.owner_id is
    'Denormalized from the car''s garage at insert time, like car_event_participants.owner_id, so "my entries" and the own-car vote check need no join.';
comment on column public.car_event_contest_entries.votes_count is
    'Trigger-owned (trg_contest_vote_counts).';
comment on column public.car_event_contest_entries.last_vote_at is
    'Trigger-owned. When this car most recently gained a vote. The tie-break: on equal counts the car that reached the count first ranks higher.';
comment on column public.car_event_contest_entries.final_rank is
    'Set once, when the contest is finished. 1..n over accepted entries. A podium (<= 3) counts only with final_votes_count >= 1.';

create index car_event_contest_entries_car_idx    on public.car_event_contest_entries (car_id);
create index car_event_contest_entries_owner_idx  on public.car_event_contest_entries (owner_id);
create index car_event_contest_entries_board_idx  on public.car_event_contest_entries (contest_id, status, votes_count desc);
create index car_event_contest_entries_podium_idx on public.car_event_contest_entries (car_id) where final_rank <= 3;

-- ---------------------------------------------------------------------------
-- 4. Votes — one row per (contest, voter). Changing your vote updates car_id.
-- ---------------------------------------------------------------------------
create table public.car_event_contest_votes (
    contest_id  uuid not null,
    voter_id    uuid not null references public.profiles(id) on update cascade on delete cascade,
    car_id      uuid not null,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    primary key (contest_id, voter_id),
    -- A vote can only point at an actual entry of the same contest, and removing the entry
    -- removes the votes that pointed at it. This composite FK is the constraint that matters;
    -- car_id deliberately has no FK of its own to cars.
    constraint car_event_contest_votes_entry_fkey
        foreign key (contest_id, car_id) references public.car_event_contest_entries (contest_id, car_id)
        on update cascade on delete cascade
);

comment on table public.car_event_contest_votes is
    'One vote per attendee per contest. Never exposed: the API returns counts and the caller''s own choice, never who voted for what.';

create index car_event_contest_votes_entry_idx on public.car_event_contest_votes (contest_id, car_id);

-- ---------------------------------------------------------------------------
-- 5. Trigger-owned counters
-- ---------------------------------------------------------------------------
create or replace function public.trg_contest_vote_counts() returns trigger
    language plpgsql
    as $$
begin
  if tg_op = 'INSERT' then
    update public.car_event_contest_entries
       set votes_count = votes_count + 1, last_vote_at = now()
     where contest_id = new.contest_id and car_id = new.car_id;
    update public.car_event_contests
       set votes_count = votes_count + 1
     where id = new.contest_id;
    return new;
  elsif tg_op = 'UPDATE' then
    if new.car_id <> old.car_id then
      update public.car_event_contest_entries
         set votes_count = votes_count - 1
       where contest_id = old.contest_id and car_id = old.car_id;
      update public.car_event_contest_entries
         set votes_count = votes_count + 1, last_vote_at = now()
       where contest_id = new.contest_id and car_id = new.car_id;
    end if;
    return new;
  elsif tg_op = 'DELETE' then
    update public.car_event_contest_entries
       set votes_count = votes_count - 1
     where contest_id = old.contest_id and car_id = old.car_id;
    update public.car_event_contests
       set votes_count = votes_count - 1
     where id = old.contest_id;
    return old;
  end if;
  return null;
end;
$$;

create trigger trg_contest_vote_counts
    after insert or update or delete on public.car_event_contest_votes
    for each row execute function public.trg_contest_vote_counts();

create or replace function public.trg_contest_entries_count() returns trigger
    language plpgsql
    as $$
begin
  if tg_op = 'INSERT' then
    if new.status = 'accepted' then
      update public.car_event_contests set entries_count = entries_count + 1 where id = new.contest_id;
    end if;
    return new;
  elsif tg_op = 'UPDATE' then
    if (old.status = 'accepted') <> (new.status = 'accepted') then
      update public.car_event_contests
         set entries_count = entries_count + (case when new.status = 'accepted' then 1 else -1 end)
       where id = new.contest_id;
    end if;
    return new;
  elsif tg_op = 'DELETE' then
    if old.status = 'accepted' then
      update public.car_event_contests set entries_count = entries_count - 1 where id = old.contest_id;
    end if;
    return old;
  end if;
  return null;
end;
$$;

create trigger trg_contest_entries_count
    after insert or update or delete on public.car_event_contest_entries
    for each row execute function public.trg_contest_entries_count();

-- ---------------------------------------------------------------------------
-- 6. Lock down — backend-gated, like every car_event* table.
-- ---------------------------------------------------------------------------
alter table public.car_event_contest_categories enable row level security;
alter table public.car_event_contests           enable row level security;
alter table public.car_event_contest_entries    enable row level security;
alter table public.car_event_contest_votes      enable row level security;

revoke all on public.car_event_contest_categories from anon, authenticated;
revoke all on public.car_event_contests           from anon, authenticated;
revoke all on public.car_event_contest_entries    from anon, authenticated;
revoke all on public.car_event_contest_votes      from anon, authenticated;

-- ---------------------------------------------------------------------------
-- 7. The car history read needs participants indexed by car (only owner_id + PK today).
-- ---------------------------------------------------------------------------
create index if not exists car_event_participants_car_id_idx on public.car_event_participants (car_id);

-- ---------------------------------------------------------------------------
-- 8. Badges: two new triggers and two seeded badges. Artwork keys must exist in the app-assets
--    bucket before the badges look right; until then the app draws its generic-medal fallback.
-- ---------------------------------------------------------------------------
alter table public.badges drop constraint if exists badges_award_trigger_known_ck;
alter table public.badges add constraint badges_award_trigger_known_ck check (
    award_trigger is null
    or award_trigger in ('account_created', 'contest_won', 'contest_podium')
);

comment on column public.badges.award_trigger is
    'The event that unlocks this badge automatically, matching the BadgeTrigger enum in the backend. NULL = hand-granted only. Known codes: account_created, contest_won, contest_podium. Adding one means extending badges_award_trigger_known_ck and BadgeTrigger in the same release.';

insert into public.badges (id, title, description, unlocked_badge_url, locked_badge_url, award_trigger) values
    ('contest_winner',  'Contest winner',  'Won a contest at a car meet.',
     'badges/contest_winner/badge-unlocked.svg',  'badges/contest_winner/badge-locked.svg',  'contest_won'),
    ('podium_finisher', 'Podium finisher', 'Placed in the top 3 of a contest at a car meet.',
     'badges/podium_finisher/badge-unlocked.svg', 'badges/podium_finisher/badge-locked.svg', 'contest_podium')
on conflict (id) do nothing;

-- ---------------------------------------------------------------------------
-- 9. Realtime: the live board rides a private broadcast topic per event,
--    `event:<event_id>:contests`. Clients may only READ, and only for approved events. There is
--    deliberately no insert policy: the backend publishes with the service key.
-- ---------------------------------------------------------------------------
create policy "contest boards readable on approved events"
on realtime.messages for select to authenticated
using (
    extension = 'broadcast'
    and realtime.topic() like 'event:%:contests'
    and exists (
        select 1
          from public.car_events e
         where e.id = split_part(realtime.topic(), ':', 2)::uuid
           and e.approval_status = 'accepted'
    )
);

commit;
