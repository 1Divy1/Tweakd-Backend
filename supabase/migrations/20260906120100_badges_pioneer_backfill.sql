-- Badges — hand `pioneer` to everyone who already earned it.
--
-- The migration before this one turned `pioneer` into a rule the backend evaluates when an account
-- finishes onboarding. That only helps accounts created from here on. Every user who signed up
-- during the launch year before this shipped earned the badge under exactly the same rule and does
-- not have it, because until now the only way to get one was a staff member typing a row.
--
-- This is the one-time catch-up. It is data, not schema, and it is deliberately a separate
-- migration from the columns it depends on: the schema change is permanent, this runs once.

begin;

-- ---------------------------------------------------------------------------
-- The same predicate the live rule uses, read off the badge row itself
-- ---------------------------------------------------------------------------
-- The window is not repeated as a literal here. It is joined out of public.badges, so the cutoff
-- has exactly one home: if the launch date moves, this migration and the running backend disagree
-- about nothing, because neither of them knows the date — they both ask the row.
--
-- `requires_onboarding = false` mirrors where the live award fires. A signed-up account that never
-- chose a username is not a member yet; awarding it a badge would put confetti in front of someone
-- the first time they ever complete onboarding, for an account they abandoned months ago.
--
-- The window is judged against `profiles.created_at` — when the account was created — because that
-- is what "was here early" means. Not when they onboarded, and not now.
--
-- ON CONFLICT DO NOTHING against user_badges_user_badge_uq covers the users who were already
-- granted `pioneer` by hand: their row stays exactly as it is, original date, original granted_by,
-- and their celebration state is not reset.
--
-- granted_by stays NULL: the backend is awarding these under a rule, not a staff member reaching
-- for a specific person. That is the same thing the live path records, and it keeps the audit
-- column meaning what it says.
--
-- granted_in_app stays at its DEFAULT false, so every user who gets a badge here sees the unlock
-- animation the next time they open the app. That is the opposite of what the granted_in_app
-- migration did for pre-existing rows, and on purpose: those users could already see their badges
-- sitting on their profile, whereas these ones are genuinely new to the person receiving them.
insert into public.user_badges (user_id, badge_id)
select p.id, b.id
  from public.profiles p
  cross join public.badges b
 where b.id = 'pioneer'
   and b.is_available
   and b.award_trigger = 'account_created'
   and p.requires_onboarding = false
   and (b.earnable_from  is null or p.created_at >= b.earnable_from)
   and (b.earnable_until is null or p.created_at <  b.earnable_until)
on conflict (user_id, badge_id) do nothing;

commit;
