-- Contest medals: one badge and one trigger per podium rank.
--
-- Replaces the original ('contest_won', 'contest_podium') pair. BadgeService.awardForTrigger
-- unlocks *every* badge bound to the trigger it is handed, so a single shared 'contest_podium'
-- could only ever back one badge — with a silver and a bronze hanging off it, all three finishers
-- would collect both. A trigger per rank is what makes three distinct medals expressible.
--
-- The old codes are dropped rather than kept: no badge row referenced them (the seeded
-- 'contest_winner' / 'podium_finisher' rows were deleted), and BadgeTrigger no longer defines
-- them, so leaving them permitted would only let the dashboard save a trigger that never fires.
--
-- Artwork keys must exist in the app-assets bucket before the badges look right; until then the
-- app draws its generic-medal fallback.

-- The two seeded rows from 20260908120000 have to go before the constraint tightens, or a replay
-- from scratch would add a CHECK the rows already in the table violate. Their unlocks go first
-- because user_badges.badge_id is ON DELETE RESTRICT — there are none in practice (the contest
-- feature has not finalised a real contest yet), but a replay must not depend on that.
delete from public.user_badges where badge_id in ('contest_winner', 'podium_finisher');
delete from public.badges where id in ('contest_winner', 'podium_finisher');

alter table public.badges drop constraint if exists badges_award_trigger_known_ck;
alter table public.badges add constraint badges_award_trigger_known_ck check (
    award_trigger is null
    or award_trigger in ('account_created',
                         'contest_first_place', 'contest_second_place', 'contest_third_place')
);

comment on column public.badges.award_trigger is
    'The event that unlocks this badge automatically, matching the BadgeTrigger enum in the backend. NULL = hand-granted only. Known codes: account_created, contest_first_place, contest_second_place, contest_third_place. Adding one means extending badges_award_trigger_known_ck and BadgeTrigger in the same release.';

insert into public.badges (id, title, description, unlocked_badge_url, locked_badge_url, award_trigger) values
    ('contest_gold',   'Gold medal',   'Took first place in a contest at a car meet.',
     'badges/contest-award-medals/gold-medal.svg',   'badges/contest-award-medals/gold-medal.svg',   'contest_first_place'),
    ('contest_silver', 'Silver medal', 'Took second place in a contest at a car meet.',
     'badges/contest-award-medals/silver-medal.svg', 'badges/contest-award-medals/silver-medal.svg', 'contest_second_place'),
    ('contest_bronze', 'Bronze medal', 'Took third place in a contest at a car meet.',
     'badges/contest-award-medals/bronze-medal.svg', 'badges/contest-award-medals/bronze-medal.svg', 'contest_third_place')
on conflict (id) do nothing;
