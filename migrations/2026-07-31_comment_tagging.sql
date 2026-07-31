-- Comment tagging (2026-07-31)
--
-- Lets a post comment (and a threaded reply to a comment — same table) tag other users and their
-- cars, mirroring the posts / forum-thread / forum-reply tagging model. A car may only be tagged
-- when its owner is tagged in the same comment, or owns it and is the comment's author (enforced in
-- PostsServiceImpl, not in SQL — the same place the other tagging rules live).
--
-- Apply to Supabase (project "Tweakd"), then regenerate the test schema dump:
--     ./scripts/dump-schema.sh
--
-- NOTE: no notification-type migration is needed — notifications.type is plain text.

CREATE TABLE public.comment_tagged_people (
    comment_id uuid NOT NULL REFERENCES public.comments(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT comment_tagged_people_pkey PRIMARY KEY (comment_id, user_id)
);

COMMENT ON TABLE public.comment_tagged_people IS
    'Profiles tagged in a post comment. A tagged car''s owner must be tagged here.';

CREATE INDEX idx_comment_tagged_people_user_id
    ON public.comment_tagged_people USING btree (user_id);

ALTER TABLE public.comment_tagged_people ENABLE ROW LEVEL SECURITY;

CREATE POLICY comment_tagged_people_select_authenticated
    ON public.comment_tagged_people FOR SELECT TO authenticated USING (true);


CREATE TABLE public.comment_tagged_cars (
    comment_id uuid NOT NULL REFERENCES public.comments(id) ON DELETE CASCADE,
    car_id uuid NOT NULL REFERENCES public.cars(id) ON DELETE CASCADE,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT comment_tagged_cars_pkey PRIMARY KEY (comment_id, car_id)
);

COMMENT ON TABLE public.comment_tagged_cars IS
    'Cars tagged in a post comment. Deleting a car silently removes its comment tags.';

CREATE INDEX idx_comment_tagged_cars_car_id
    ON public.comment_tagged_cars USING btree (car_id);

ALTER TABLE public.comment_tagged_cars ENABLE ROW LEVEL SECURITY;

CREATE POLICY comment_tagged_cars_select_authenticated
    ON public.comment_tagged_cars FOR SELECT TO authenticated USING (true);
