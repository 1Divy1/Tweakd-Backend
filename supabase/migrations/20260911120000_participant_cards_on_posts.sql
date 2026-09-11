-- Participant cards: a post can share one.
--
-- A participant card is not stored. It is derived on read from an (event_id, car_id) pair — an
-- accepted row in car_event_participants of an event an organizer has marked finished — so nobody,
-- the owner included, can create or edit one, and it can never go stale or be forged. A post that
-- shares a card stores only that pair; the backend re-derives the card every time the post is read.
--
-- The reference is a composite foreign key onto car_event_participants' primary key, so the pair
-- must be a real participant row. ON DELETE SET NULL clears both columns together when that row
-- goes (deleting the car or the event cascades into car_event_participants), and the post degrades
-- to a plain one instead of breaking the feed.

alter table public.posts
    add column participant_card_event_id uuid,
    add column participant_card_car_id   uuid;

alter table public.posts
    add constraint posts_participant_card_both_or_neither_ck check (
        (participant_card_event_id is null) = (participant_card_car_id is null)
    );

alter table public.posts
    add constraint posts_participant_card_fkey
        foreign key (participant_card_event_id, participant_card_car_id)
        references public.car_event_participants (event_id, car_id)
        on update cascade on delete set null;

-- The repost cooldown asks "was this card posted recently?"; this index answers it, and stays tiny
-- because ordinary posts are excluded.
create index posts_participant_card_recent_idx
    on public.posts (participant_card_event_id, participant_card_car_id, created_at desc)
    where participant_card_event_id is not null;

comment on column public.posts.participant_card_event_id is
    'Event half of the participant card this post shares (with participant_card_car_id: both or neither). The card itself is derived on read by the backend, never stored.';
comment on column public.posts.participant_card_car_id is
    'Car half of the participant card this post shares (with participant_card_event_id: both or neither).';
