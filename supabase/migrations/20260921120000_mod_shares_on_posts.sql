-- Mod shares: a post can share one build-log modification, and a modification can hide its price.
--
-- Two related changes, one migration, because the feed card is the first surface that would have
-- published a price the owner never agreed to publish.
--
-- 1. car_modifications.is_price_public
--
-- Price is optional and, from here on, private by default: a user can skip it, record it only for
-- their own expense tracking, or deliberately publish it. The flag governs every non-owner read --
-- the in-app car detail, the public car page and the feed card -- not just the feed, so there is
-- one flag with one meaning. Owners always see their own prices.
--
-- Note the default flips existing behaviour: prices that are visible to other users today become
-- owner-only after this migration, public car pages included. That is deliberate -- nobody opted
-- into publishing them.
--
-- 2. posts.mod_share_modification_id
--
-- A shared mod is an ordinary post that tags the car and stores the modification's id. The card is
-- derived on every read from car_modifications (the same contract as participant cards), so an
-- edited mod updates everywhere and a stored card can never go stale or be forged. ON DELETE SET
-- NULL degrades the post to a plain one when the mod goes, keeping its likes and comments rather
-- than breaking the feed.

-- ── 1. Price visibility ──────────────────────────────────────────────────────────────────────

alter table public.car_modifications
    add column is_price_public boolean not null default false;

-- A mod cannot publish a price it does not have. The application enforces the same rule at
-- validation time; this is the database half of it.
alter table public.car_modifications
    add constraint car_modifications_price_visibility_check check (
        is_price_public = false or price is not null
    );

comment on column public.car_modifications.is_price_public is
    'Whether non-owners may see this mod''s price. False (the default) keeps it owner-only across the app, the public car page and the feed; the owner always sees it.';

-- ── 2. Mod shares on posts ───────────────────────────────────────────────────────────────────

alter table public.posts
    add column mod_share_modification_id uuid;

alter table public.posts
    add constraint posts_mod_share_modification_fkey
        foreign key (mod_share_modification_id)
        references public.car_modifications (id)
        on update cascade on delete set null;

-- A mod is a one-time event, so it gets one post -- unlike a participant card, which may deserve a
-- re-share and is rate-limited instead. The service returns the post it finds, making a double tap
-- a no-op; this index is what makes that hold under two simultaneous saves.
create unique index posts_mod_share_modification_uq
    on public.posts (mod_share_modification_id)
    where mod_share_modification_id is not null;

comment on column public.posts.mod_share_modification_id is
    'The build-log modification this post shares. The card drawn in place of the post''s images is derived on read from car_modifications, never stored. Null on an ordinary post, and on one whose mod has since been deleted.';
