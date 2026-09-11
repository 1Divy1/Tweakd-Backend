-- Contests open and close by hand, never on a clock.
--
-- The original design had the backend sweep every 15 seconds: opening contests whose `opens_at`
-- had passed and finalising those whose `closes_at` had. That is gone. An organizer opens voting
-- when they want it open and finishes it when they want it closed; the only other thing that
-- closes a contest is the event itself being marked finished or cancelled, which already
-- finalises every open contest in the same transaction.
--
-- `opens_at` and `closes_at` stay, demoted to what the app shows attendees ("Voting 18:30 →
-- 22:30"). Nothing enforces them, so the two partial indexes that existed purely to serve the
-- sweep's due-queries have no reader left.

begin;

drop index if exists public.car_event_contests_due_open_idx;
drop index if exists public.car_event_contests_due_close_idx;

comment on table public.car_event_contests is
    'A vote that runs inside a car event. scheduled = published, entries open, voting locked until an organizer opens it; open = voting; finished = results final (top 3 awarded); canceled is reserved. Status only ever moves because an organizer acted, or because the event was finished/cancelled — never on a timer.';

comment on column public.car_event_contests.opens_at is
    'The *planned* start of voting, shown in the app. Not enforced: a contest is scheduled until an organizer opens it.';
comment on column public.car_event_contests.closes_at is
    'The *planned* end of voting, shown in the app. Not enforced: a contest stays open until an organizer finishes it, or until the event is marked finished or cancelled.';
comment on column public.car_event_contests.finished_by is
    'The organizer who finished it. NULL = closed by the event''s own finish/cancel cascade. Not an FK: a departed organizer must not take the result with them.';
comment on column public.car_event_contests.finished_early is
    'Whether it was finished before its planned closes_at. Presentation only.';

commit;
