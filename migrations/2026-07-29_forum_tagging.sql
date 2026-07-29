-- Forum tagging (2026-07-29)
--
-- Lets a forum thread and a forum thread reply tag other users and their cars, mirroring the
-- posts module's tagged_people / tagged_cars model. A car may only be tagged when its owner is
-- tagged in the same thread/reply (enforced in ForumsServiceImpl, not in SQL — the same place the
-- posts rule lives).
--
-- Apply to Supabase (project "Tweakd"), then regenerate the test schema dump:
--     ./scripts/dump-schema.sh
--
-- NOTE: notification_preferences.tags_enabled is NOT created here — it was already added
-- manually before this migration.

-- ---------------------------------------------------------------------------
-- Thread-level tags
-- ---------------------------------------------------------------------------

CREATE TABLE public.forum_thread_tagged_people (
    thread_id uuid NOT NULL REFERENCES public.forum_threads(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT forum_thread_tagged_people_pkey PRIMARY KEY (thread_id, user_id)
);

COMMENT ON TABLE public.forum_thread_tagged_people IS
    'Profiles tagged in a forum thread (the OP). A tagged car''s owner must be tagged here.';

CREATE INDEX idx_forum_thread_tagged_people_user_id
    ON public.forum_thread_tagged_people USING btree (user_id);

ALTER TABLE public.forum_thread_tagged_people ENABLE ROW LEVEL SECURITY;

CREATE POLICY forum_thread_tagged_people_select_authenticated
    ON public.forum_thread_tagged_people FOR SELECT TO authenticated USING (true);


CREATE TABLE public.forum_thread_tagged_cars (
    thread_id uuid NOT NULL REFERENCES public.forum_threads(id) ON DELETE CASCADE,
    car_id uuid NOT NULL REFERENCES public.cars(id) ON DELETE CASCADE,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT forum_thread_tagged_cars_pkey PRIMARY KEY (thread_id, car_id)
);

COMMENT ON TABLE public.forum_thread_tagged_cars IS
    'Cars tagged in a forum thread (the OP). Deleting a car silently removes its thread tags.';

CREATE INDEX idx_forum_thread_tagged_cars_car_id
    ON public.forum_thread_tagged_cars USING btree (car_id);

ALTER TABLE public.forum_thread_tagged_cars ENABLE ROW LEVEL SECURITY;

CREATE POLICY forum_thread_tagged_cars_select_authenticated
    ON public.forum_thread_tagged_cars FOR SELECT TO authenticated USING (true);


-- ---------------------------------------------------------------------------
-- Reply-level tags
-- ---------------------------------------------------------------------------

CREATE TABLE public.forum_thread_reply_tagged_people (
    reply_id uuid NOT NULL REFERENCES public.forum_thread_replies(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT forum_thread_reply_tagged_people_pkey PRIMARY KEY (reply_id, user_id)
);

COMMENT ON TABLE public.forum_thread_reply_tagged_people IS
    'Profiles tagged in a forum thread reply. A tagged car''s owner must be tagged here.';

CREATE INDEX idx_forum_thread_reply_tagged_people_user_id
    ON public.forum_thread_reply_tagged_people USING btree (user_id);

ALTER TABLE public.forum_thread_reply_tagged_people ENABLE ROW LEVEL SECURITY;

CREATE POLICY forum_thread_reply_tagged_people_select_authenticated
    ON public.forum_thread_reply_tagged_people FOR SELECT TO authenticated USING (true);


CREATE TABLE public.forum_thread_reply_tagged_cars (
    reply_id uuid NOT NULL REFERENCES public.forum_thread_replies(id) ON DELETE CASCADE,
    car_id uuid NOT NULL REFERENCES public.cars(id) ON DELETE CASCADE,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT forum_thread_reply_tagged_cars_pkey PRIMARY KEY (reply_id, car_id)
);

COMMENT ON TABLE public.forum_thread_reply_tagged_cars IS
    'Cars tagged in a forum thread reply. Deleting a car silently removes its reply tags.';

CREATE INDEX idx_forum_thread_reply_tagged_cars_car_id
    ON public.forum_thread_reply_tagged_cars USING btree (car_id);

ALTER TABLE public.forum_thread_reply_tagged_cars ENABLE ROW LEVEL SECURITY;

CREATE POLICY forum_thread_reply_tagged_cars_select_authenticated
    ON public.forum_thread_reply_tagged_cars FOR SELECT TO authenticated USING (true);
