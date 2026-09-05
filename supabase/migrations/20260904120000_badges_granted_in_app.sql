-- Badges — the "has the app played the unlock animation yet?" flag.
--
-- When a badge is awarded (automatically by the backend, or by hand from the dashboard) the app
-- owes the user a one-time Duolingo-style celebration the next time they open it. The client needs
-- to know which freshly-earned badges it still has to animate, and the backend needs to remember
-- that it has, so the animation fires exactly once and never again.
--
-- That is all this column is: a per-unlock latch the client raises after it has shown the
-- animation. It is not part of what a badge *is* — a held badge is a held badge whether or not its
-- animation has played — so it does not touch any of the existing reads.

begin;

-- ---------------------------------------------------------------------------
-- 1. The flag
-- ---------------------------------------------------------------------------
-- false = earned but not yet celebrated in the app; the client owes an animation.
-- true  = the client has played the unlock animation, or the row predates this feature.
--
-- NOT NULL with a false default: a new unlock always starts owing an animation, and the award
-- path (BadgeService.award / grant) never sets it — it relies on this default, exactly as it
-- already relies on the created_at default.
alter table public.user_badges
    add column if not exists granted_in_app boolean not null default false;

comment on column public.user_badges.granted_in_app is
    'Has the app played the one-time unlock animation for this badge? false = still owed. Raised by the client via POST /api/v1/badges/me/pending-celebration/{badgeId} after it animates. Not a fact about the badge — purely client-celebration bookkeeping.';

-- ---------------------------------------------------------------------------
-- 2. Backfill: do not retro-celebrate
-- ---------------------------------------------------------------------------
-- Every badge already on a profile was earned before this feature existed. Their owners have long
-- since seen them sitting on their profile; replaying an animation for each one on next app open
-- would be a pile of confetti for nothing. Mark them all as already celebrated.
--
-- Runs once, at migration time, so "every current row" is exactly "every pre-feature row". Rows
-- inserted afterwards get false from the column default and are celebrated normally.
update public.user_badges
   set granted_in_app = true
 where granted_in_app = false;

-- No index. The only query is "this caller's un-celebrated badges", filtered by user_id — already
-- served by user_badges_user_badge_uq (user_id, badge_id) — and a user holds a handful of badges,
-- so the granted_in_app filter is a cheap in-memory pass over that handful. A partial index here
-- would be maintenance on every award for a scan the module never does, the same trade-off the
-- hardening migration already declined for (user_id, created_at).

commit;
