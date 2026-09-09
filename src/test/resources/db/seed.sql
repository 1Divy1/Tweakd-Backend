-- Reference-data seed for Testcontainers ITs, loaded AFTER schema.sql.
--
-- The schema dump is schema-only (pg_dump --schema-only), so Supabase's seeded lookup
-- rows are absent. Any lookup row that a NOT NULL / RESTRICT foreign key depends on must
-- be seeded here, or inserts into the referencing table fail the FK.
--
-- profiles.app_language is NOT NULL DEFAULT 'en' with a RESTRICT FK to
-- app_language_options(id), so every profile insert (including the JdbcTemplate fixtures
-- that only set id + username) needs these codes present.
INSERT INTO public.app_language_options (id, language) VALUES
    ('en', 'English'),
    ('ro', 'Romanian')
ON CONFLICT (id) DO NOTHING;

-- Minimal car reference data so an IT can insert a real `cars` row (every `cars` FK target must
-- exist). Used by the dms car-tag repository IT; harmless to other modules, which don't read these
-- lookup tables. Fixed ids so tests can reference them directly.
INSERT INTO public.car_brands (id, name) VALUES
    ('00000000-0000-0000-0000-0000000b0001', 'Test Brand')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.car_models (id, brand_id, model) VALUES
    ('00000000-0000-0000-0000-0000000d0001', '00000000-0000-0000-0000-0000000b0001', 'Test Model')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.car_color_options (id, name, color_code) VALUES
    ('test_color', 'Test Color', '#000000')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.car_drivetrain_options (id, name) VALUES
    ('test_dt', 'Test Drivetrain')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.car_distance_units (id, name) VALUES
    ('test_km', 'Test Kilometers')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.car_status_options (id, type) VALUES
    ('test_status', 'Test Status')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.car_fuel_type_options (id, name) VALUES
    ('test_fuel', 'Test Fuel')
ON CONFLICT (id) DO NOTHING;

-- Business reference data. business_accounts has RESTRICT FKs to all three lookup tables plus
-- cities, so a business row cannot be inserted without these. The status ids must match the real
-- Supabase values, because the visibility filter compares against them as literals.
INSERT INTO public.countries (id, name) VALUES
    ('RO', 'Romania')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.cities (id, name, region, country, location) VALUES
    ('test-city', 'Test City', 'Test Region', 'RO',
     public.ST_SetSRID(public.ST_MakePoint(23.6236, 46.7712), 4326)::public.geography)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.business_type_options (id, type) VALUES
    ('test_business_type', 'Test Business Type'),
    ('test_other_type', 'Test Other Type')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.business_account_verification_status_options (id, status) VALUES
    ('pending', 'Pending'),
    ('verified', 'Verified'),
    ('rejected', 'Rejected')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.business_account_active_status_options (id, status) VALUES
    ('active', 'Active'),
    ('suspended', 'Suspended'),
    ('deleted', 'Deleted')
ON CONFLICT (id) DO NOTHING;

-- Map-events reference data. car_events has RESTRICT FKs to the category, status and approval
-- status tables, and the two join tables have RESTRICT FKs to their own status tables, so no event
-- row can be inserted without these. The ids must match the real Supabase values: the module
-- compares against them as literals (both in Java constants and in the native map query's
-- `status not in ('hidden','canceled','previous')`).
INSERT INTO public.car_event_categories (id, category, is_available) VALUES
    ('car_meet', 'Car meet', true),
    ('test_unavailable_category', 'Test Unavailable Category', false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.car_event_status_options (id, status) VALUES
    ('upcoming', 'Upcoming'),
    ('live', 'Live'),
    ('previous', 'Previous'),
    ('hidden', 'Hidden'),
    ('canceled', 'Canceled')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.car_event_approval_status_options (id, status) VALUES
    ('pending', 'Pending'),
    ('accepted', 'Accepted'),
    ('rejected', 'Rejected')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.car_event_attendee_status (id, status) VALUES
    ('attending', 'Attending'),
    ('interested', 'Interested')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.car_event_participant_status_options (id, status) VALUES
    ('pending', 'Pending'),
    ('accepted', 'Accepted'),
    ('rejected', 'Rejected'),
    ('withdrawn', 'Withdrawn')
ON CONFLICT (id) DO NOTHING;

-- Feedback feed: feedback_feed_messages.type and .status both carry RESTRICT FKs into these
-- lookup tables, so no message can be inserted without them.
INSERT INTO public.feedback_feed_feedback_types (id, type) VALUES
    ('bug', 'Bug'),
    ('feature_request', 'Feature request'),
    ('feature_improvement', 'Feature improvement')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.feedback_feed_status_options (id, status) VALUES
    ('sent', 'Sent'),
    ('under_development', 'Under development'),
    ('completed', 'Completed')
ON CONFLICT (id) DO NOTHING;

-- Contest categories: car_event_contests.category_id is a RESTRICT FK.
INSERT INTO public.car_event_contest_categories (id, label, icon, sort_order) VALUES
  ('exhaust',  'Best exhaust system', 'exhaust',  10),
  ('wheels',   'Best wheels',         'wheels',   20),
  ('paint',    'Best paint / wrap',   'paint',    30),
  ('interior', 'Best interior',       'interior', 40),
  ('loudest',  'Loudest',             'loud',     50),
  ('custom',   'Custom category',     'trophy',   99)
ON CONFLICT (id) DO NOTHING;
