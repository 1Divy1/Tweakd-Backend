-- GENERATED FILE - do not edit by hand. Regenerate with ./scripts/dump-schema.sh
-- Schema dump of the Supabase 'public' schema for Testcontainers-based tests,
-- plus a minimal stub of the Supabase-managed bits the public schema references.

-- PostGIS is installed in the public schema on the live Supabase project, so the dump
-- references public.geography — install it the same way here.
CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA public;

-- Minimal stub of Supabase's auth schema (only what public-schema FKs/functions touch).
-- Test fixtures insert into auth.users + profiles directly; the real handle_new_user
-- trigger on auth.users is NOT recreated here.
CREATE SCHEMA IF NOT EXISTS auth;
CREATE TABLE auth.users (
    id uuid PRIMARY KEY,
    email text,
    raw_app_meta_data jsonb DEFAULT '{}'::jsonb,
    raw_user_meta_data jsonb DEFAULT '{}'::jsonb,
    created_at timestamptz DEFAULT now()
);
CREATE OR REPLACE FUNCTION auth.uid() RETURNS uuid
    LANGUAGE sql STABLE AS $$ SELECT NULL::uuid $$;
CREATE OR REPLACE FUNCTION auth.role() RETURNS text
    LANGUAGE sql STABLE AS $$ SELECT NULL::text $$;

--
-- PostgreSQL database dump
--


-- Dumped from database version 17.6
-- Dumped by pg_dump version 17.10

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--



--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS 'standard public schema';


--
-- Name: report_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.report_status AS ENUM (
    'pending',
    'in_progress',
    'resolved',
    'dismissed'
);


--
-- Name: target_entity; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.target_entity AS ENUM (
    'post',
    'comment',
    'profile',
    'forum_thread',
    'forum_thread_reply'
);


--
-- Name: bump_feedback_comment_count(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.bump_feedback_comment_count() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
begin
  if tg_op = 'INSERT' then
    update public.feedback set comment_count = comment_count + 1 where id = new.feedback_id;
    return new;
  else
    update public.feedback set comment_count = greatest(comment_count - 1, 0) where id = old.feedback_id;
    return old;
  end if;
end $$;


--
-- Name: bump_feedback_vote_count(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.bump_feedback_vote_count() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
begin
  if tg_op = 'INSERT' then
    update public.feedback set vote_count = vote_count + 1 where id = new.feedback_id;
    return new;
  else
    update public.feedback set vote_count = greatest(vote_count - 1, 0) where id = old.feedback_id;
    return old;
  end if;
end $$;


--
-- Name: compute_post_ranking_score(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.compute_post_ranking_score() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO ''
    AS $$
declare
  w_like        constant double precision := 1;
  w_comment     constant double precision := 4;
  w_share_plain constant double precision := 6;
  w_share_desc  constant double precision := 10;
  w_save        constant double precision := 3;
  plain_shares  bigint;
  s             double precision;
begin
  plain_shares := greatest(coalesce(new.shares_count, 0) - coalesce(new.quote_shares_count, 0), 0);

  s := w_like        * coalesce(new.likes_count, 0)
     + w_comment     * coalesce(new.comments_count, 0)
     + w_share_plain * plain_shares
     + w_share_desc  * coalesce(new.quote_shares_count, 0)
     + w_save        * coalesce(new.saved_count, 0);

  new.ranking_score :=
        log(greatest(s, 1))
      + extract(epoch from coalesce(new.created_at, now())) / 45000.0;

  return new;
end;
$$;


--
-- Name: fn_forum_post_likes_count(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_forum_post_likes_count() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
begin
    if tg_op = 'INSERT' then
        update public.forum_posts set likes_count = likes_count + 1 where id = new.post_id;
        return new;
    elsif tg_op = 'DELETE' then
        update public.forum_posts set likes_count = greatest(likes_count - 1, 0) where id = old.post_id;
        return old;
    end if;
    return null;
end;
$$;


--
-- Name: fn_forum_posts_counts(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_forum_posts_counts() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
begin
    if tg_op = 'INSERT' then
        update public.forum_threads
            set reply_count = reply_count + 1, last_activity_at = now()
            where id = new.thread_id;
        if new.parent_post_id is not null then
            update public.forum_posts set reply_count = reply_count + 1 where id = new.parent_post_id;
        end if;
        return new;
    elsif tg_op = 'DELETE' then
        update public.forum_threads
            set reply_count = greatest(reply_count - 1, 0)
            where id = old.thread_id;
        if old.parent_post_id is not null then
            update public.forum_posts set reply_count = greatest(reply_count - 1, 0) where id = old.parent_post_id;
        end if;
        return old;
    end if;
    return null;
end;
$$;


--
-- Name: fn_forum_thread_likes_count(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_forum_thread_likes_count() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
begin
    if tg_op = 'INSERT' then
        update public.forum_threads set likes_count = likes_count + 1 where id = new.thread_id;
        return new;
    elsif tg_op = 'DELETE' then
        update public.forum_threads set likes_count = greatest(likes_count - 1, 0) where id = old.thread_id;
        return old;
    end if;
    return null;
end;
$$;


--
-- Name: fn_forum_threads_ranking(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_forum_threads_ranking() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
declare
    v_engagement numeric;
begin
    v_engagement := new.likes_count + 4 * new.reply_count;
    new.ranking_score := log(greatest(v_engagement, 1))
        + (extract(epoch from new.created_at) - 1700000000) / 45000.0;
    return new;
end;
$$;


--
-- Name: fn_forum_threads_set_brand(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_forum_threads_set_brand() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
begin
    if new.model_id is not null then
        select brand_id into new.brand_id from public.car_models where id = new.model_id;
        if new.brand_id is null then
            raise exception 'forum_threads: model_id % has no matching brand', new.model_id;
        end if;
    end if;
    return new;
end;
$$;


--
-- Name: forum_thread_topics_count(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.forum_thread_topics_count() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
begin
  if tg_op = 'INSERT' then
    update forum_topics set thread_count = thread_count + 1 where id = new.topic_id;
  elsif tg_op = 'DELETE' then
    update forum_topics set thread_count = thread_count - 1 where id = old.topic_id;
  end if;
  return null;
end;
$$;


--
-- Name: forum_threads_category_counts(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.forum_threads_category_counts() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
begin
  if tg_op = 'INSERT' then
    if new.brand_id is not null then
      update car_brands set thread_count = thread_count + 1 where id = new.brand_id;
    end if;
    if new.model_id is not null then
      update car_models set thread_count = thread_count + 1 where id = new.model_id;
    end if;
  elsif tg_op = 'DELETE' then
    if old.brand_id is not null then
      update car_brands set thread_count = thread_count - 1 where id = old.brand_id;
    end if;
    if old.model_id is not null then
      update car_models set thread_count = thread_count - 1 where id = old.model_id;
    end if;
  end if;
  return null;
end;
$$;


--
-- Name: handle_follow_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.handle_follow_change() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
BEGIN
    -- 1. INSERT direct accepted (follow la profil public)
    IF TG_OP = 'INSERT' AND NEW.status = 'accepted' THEN
        UPDATE public.profiles
            SET following_count = following_count + 1 WHERE id = NEW.follower_id;
        UPDATE public.profiles
            SET followers_count = followers_count + 1 WHERE id = NEW.following_id;
            
    -- 2. UPDATE de la pending la accepted (profilul privat a acceptat cererea)
    ELSIF TG_OP = 'UPDATE' AND OLD.status = 'pending' AND NEW.status = 'accepted' THEN
        UPDATE public.profiles
            SET following_count = following_count + 1 WHERE id = NEW.follower_id;
        UPDATE public.profiles
            SET followers_count = followers_count + 1 WHERE id = NEW.following_id;
            
    -- 3. DELETE (unfollow sau remove follower). Scădem doar dacă era deja accepted.
    -- (Dacă se șterge o cerere pending, nu modificăm contoarele)
    ELSIF TG_OP = 'DELETE' AND OLD.status = 'accepted' THEN
        UPDATE public.profiles
            SET following_count = GREATEST(following_count - 1, 0) WHERE id = OLD.follower_id;
        UPDATE public.profiles
            SET followers_count = GREATEST(followers_count - 1, 0) WHERE id = OLD.following_id;
    END IF;
    RETURN NULL;
END;
$$;


--
-- Name: handle_new_profile_garage(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.handle_new_profile_garage() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
BEGIN
    INSERT INTO public.garages (owner_id)
    VALUES (NEW.id);
    RETURN NEW;
END;
$$;


--
-- Name: FUNCTION handle_new_profile_garage(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.handle_new_profile_garage() IS 'Auto-creates a garage row for each new profile. SECURITY DEFINER so it bypasses RLS.';


--
-- Name: handle_new_user(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.handle_new_user() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
begin
  -- Staff accounts (admin dashboard) get no app profile.
  if new.raw_app_meta_data ->> 'role' = 'admin'
     or (new.raw_user_meta_data ->> 'is_staff')::boolean is true then
    return new;
  end if;

  insert into public.profiles (
    id,
    name,
    avatar_url,
    bio,
    external_link,
    followers_count,
    following_count,
    is_verified,
    is_business,
    role,
    requires_onboarding
  ) values (
    new.id,

    -- full name / display name
    coalesce(
      new.raw_user_meta_data ->> 'full_name',
      new.raw_user_meta_data ->> 'name',
      nullif(trim(concat_ws(' ', new.raw_user_meta_data ->> 'first_name', new.raw_user_meta_data ->> 'last_name')), ''),
      nullif(trim(concat_ws(' ', new.raw_user_meta_data ->> 'given_name', new.raw_user_meta_data ->> 'family_name')), ''),
      ''
    ),

    -- non-nullable in your schema
    coalesce(new.raw_user_meta_data ->> 'avatar_url', new.raw_user_meta_data ->> 'picture', ''),

    -- non-nullable in your schema
    coalesce(new.raw_user_meta_data ->> 'bio', ''),

    -- non-nullable in your schema
    coalesce(new.raw_user_meta_data ->> 'external_link', ''),

    -- have defaults in schema, but set explicitly for safety
    0,
    0,

    -- have defaults in schema, but set explicitly for safety
    false,
    false,
    'user',
    true
  );

  return new;
end;
$$;


--
-- Name: resolve_moderation_cases_for_deleted_target(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.resolve_moderation_cases_for_deleted_target() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
declare
  t_type text := tg_argv[0];
begin
  update public.moderation_cases
     set status      = 'resolved',
         resolution  = 'content_deleted',
         resolved_at = now(),
         resolved_by = null
   where target_type = t_type
     and target_id   = old.id
     and status in ('open', 'escalated');
  return old;
end $$;


--
-- Name: rls_auto_enable(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.rls_auto_enable() RETURNS event_trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog'
    AS $$
DECLARE
  cmd record;
BEGIN
  FOR cmd IN
    SELECT *
    FROM pg_event_trigger_ddl_commands()
    WHERE command_tag IN ('CREATE TABLE', 'CREATE TABLE AS', 'SELECT INTO')
      AND object_type IN ('table','partitioned table')
  LOOP
     IF cmd.schema_name IS NOT NULL AND cmd.schema_name IN ('public') AND cmd.schema_name NOT IN ('pg_catalog','information_schema') AND cmd.schema_name NOT LIKE 'pg_toast%' AND cmd.schema_name NOT LIKE 'pg_temp%' THEN
      BEGIN
        EXECUTE format('alter table if exists %s enable row level security', cmd.object_identity);
        RAISE LOG 'rls_auto_enable: enabled RLS on %', cmd.object_identity;
      EXCEPTION
        WHEN OTHERS THEN
          RAISE LOG 'rls_auto_enable: failed to enable RLS on %', cmd.object_identity;
      END;
     ELSE
        RAISE LOG 'rls_auto_enable: skip % (either system schema or not in enforced list: %.)', cmd.object_identity, cmd.schema_name;
     END IF;
  END LOOP;
END;
$$;


--
-- Name: set_follow_initial_status(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_follow_initial_status() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
begin
    new.status := 'accepted';
    return new;
end;
$$;


--
-- Name: sync_comment_likes_count(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_comment_likes_count() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
begin
  if (tg_op = 'INSERT') then
    update public.comments set likes_count = likes_count + 1 where id = new.comment_id;
  elsif (tg_op = 'DELETE') then
    update public.comments set likes_count = likes_count - 1 where id = old.comment_id;
  end if;
  return null;
end;
$$;


--
-- Name: sync_post_comments_count(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_post_comments_count() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
begin
  if (tg_op = 'INSERT') then
    if (new.is_deleted = false) then
      update public.posts set comments_count = comments_count + 1 where id = new.post_id;
    end if;
  elsif (tg_op = 'DELETE') then
    if (old.is_deleted = false) then
      update public.posts set comments_count = comments_count - 1 where id = old.post_id;
    end if;
  elsif (tg_op = 'UPDATE') then
    if (old.is_deleted = false and new.is_deleted = true) then
      update public.posts set comments_count = comments_count - 1 where id = new.post_id;
    elsif (old.is_deleted = true and new.is_deleted = false) then
      update public.posts set comments_count = comments_count + 1 where id = new.post_id;
    end if;
  end if;
  return null;
end;
$$;


--
-- Name: sync_post_likes_count(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_post_likes_count() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
begin
  if (tg_op = 'INSERT') then
    update public.posts set likes_count = likes_count + 1 where id = new.post_id;
  elsif (tg_op = 'DELETE') then
    update public.posts set likes_count = likes_count - 1 where id = old.post_id;
  end if;
  return null;
end;
$$;


--
-- Name: sync_post_saved_count(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_post_saved_count() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO ''
    AS $$
begin
  if (tg_op = 'INSERT') then
    update public.posts set saved_count = saved_count + 1 where id = new.post_id;
  elsif (tg_op = 'DELETE') then
    update public.posts set saved_count = saved_count - 1 where id = old.post_id;
  end if;
  return null;
end;
$$;


--
-- Name: sync_post_shares_count(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_post_shares_count() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO ''
    AS $$
begin
  if (tg_op = 'INSERT') then
    if nullif(btrim(new.content), '') is not null then
      update public.posts set quote_shares_count = quote_shares_count + 1 where id = new.post_id;
    else
      update public.posts set shares_count = shares_count + 1 where id = new.post_id;
    end if;
  elsif (tg_op = 'DELETE') then
    if nullif(btrim(old.content), '') is not null then
      update public.posts set quote_shares_count = quote_shares_count - 1 where id = old.post_id;
    else
      update public.posts set shares_count = shares_count - 1 where id = old.post_id;
    end if;
  end if;
  return null;
end;
$$;


--
-- Name: sync_role_to_auth(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_role_to_auth() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
BEGIN
  UPDATE auth.users
  SET raw_app_meta_data = jsonb_set(
    COALESCE(raw_app_meta_data, '{}'::jsonb),
    '{role}',
    to_jsonb(NEW.role)
  )
  WHERE id = NEW.id;
  RETURN NEW;
END;
$$;


--
-- Name: touch_ticket_on_message(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.touch_ticket_on_message() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
begin
  update public.support_tickets
     set last_message_at = new.created_at,
         updated_at      = now()
   where id = new.ticket_id;
  return new;
end $$;


--
-- Name: update_comment_reply_count(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_comment_reply_count() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
begin
  if (tg_op = 'INSERT') then
    if new.parent_comment_id is not null then
      update public.comments
      set reply_count = reply_count + 1
      where id = new.parent_comment_id;
    end if;
    return new;

  elsif (tg_op = 'DELETE') then
    if old.parent_comment_id is not null then
      update public.comments
      set reply_count = greatest(reply_count - 1, 0)
      where id = old.parent_comment_id;
    end if;
    return old;
  end if;

  return null;
end;
$$;


--
-- Name: upsert_moderation_case(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.upsert_moderation_case() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
declare
  t_type text := tg_argv[0];
  t_id   uuid := ((to_jsonb(new) ->> tg_argv[1]))::uuid;
begin
  insert into public.moderation_cases (target_type, target_id, last_reported_at)
  values (t_type, t_id, coalesce(new.created_at, now()))
  on conflict (target_type, target_id) do update
    set last_reported_at = excluded.last_reported_at,
        status      = case when public.moderation_cases.status = 'resolved' then 'open' else public.moderation_cases.status end,
        resolution  = case when public.moderation_cases.status = 'resolved' then null   else public.moderation_cases.resolution end,
        resolved_by = case when public.moderation_cases.status = 'resolved' then null   else public.moderation_cases.resolved_by end,
        resolved_at = case when public.moderation_cases.status = 'resolved' then null   else public.moderation_cases.resolved_at end;
  return new;
end $$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: admin_team_members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_team_members (
    user_id uuid NOT NULL,
    role text NOT NULL,
    status text DEFAULT 'invited'::text NOT NULL,
    invited_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    last_active_at timestamp with time zone,
    email text NOT NULL,
    display_name text NOT NULL,
    avatar_url text,
    CONSTRAINT admin_team_members_role_check CHECK ((role = ANY (ARRAY['owner'::text, 'senior_admin'::text, 'content_moderator'::text, 'support_agent'::text, 'technical'::text]))),
    CONSTRAINT admin_team_members_status_check CHECK ((status = ANY (ARRAY['invited'::text, 'active'::text])))
);


--
-- Name: TABLE admin_team_members; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.admin_team_members IS 'Admin-dashboard staff. A row here grants fine-grained dashboard capabilities to a normal auth user; the coarse gate is the JWT app_metadata.role = admin claim. Roles: owner, senior_admin, content_moderator, support_agent, technical.';


--
-- Name: app_language_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_language_options (
    id text NOT NULL,
    language text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE app_language_options; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.app_language_options IS 'Language options for the app''s interface';


--
-- Name: blocked_accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.blocked_accounts (
    blocker_id uuid NOT NULL,
    blocked_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: car_brands; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_brands (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    created_at timestamp with time zone DEFAULT (now() AT TIME ZONE 'utc'::text) NOT NULL,
    name text NOT NULL,
    thread_count integer DEFAULT 0 NOT NULL
);


--
-- Name: TABLE car_brands; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.car_brands IS 'A list of predefined car brands that a user can select from (to avoid manual entries).';


--
-- Name: car_category_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_category_options (
    id text NOT NULL,
    name text NOT NULL
);


--
-- Name: car_color_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_color_options (
    id text NOT NULL,
    name text NOT NULL,
    color_code text NOT NULL
);


--
-- Name: COLUMN car_color_options.color_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.car_color_options.color_code IS 'The hexadecimal code to render the color in the mobile frontend.';


--
-- Name: car_distance_units; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_distance_units (
    id text NOT NULL,
    name text NOT NULL
);


--
-- Name: TABLE car_distance_units; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.car_distance_units IS 'Distance units (km, mi). Set per car via cars.mileage_unit_id.';


--
-- Name: car_drivetrain_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_drivetrain_options (
    id text NOT NULL,
    name text NOT NULL
);


--
-- Name: car_fuel_type_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_fuel_type_options (
    id text NOT NULL,
    name text NOT NULL
);


--
-- Name: TABLE car_fuel_type_options; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.car_fuel_type_options IS 'Drop-down style fuel type options (eg: petrol, diesel, hybrid, electric)';


--
-- Name: car_gallery; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_gallery (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    car_id uuid NOT NULL,
    url text NOT NULL,
    "position" integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE car_gallery; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.car_gallery IS 'Image / Video gallery for individual car builds in the garage (not images from the posts section).';


--
-- Name: car_mod_categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_mod_categories (
    id text NOT NULL,
    mod_name text NOT NULL
);


--
-- Name: TABLE car_mod_categories; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.car_mod_categories IS 'A list of predefined mods for a car';


--
-- Name: car_models; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_models (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    brand_id uuid NOT NULL,
    model text NOT NULL,
    created_at timestamp with time zone DEFAULT (now() AT TIME ZONE 'utc'::text) NOT NULL,
    thread_count integer DEFAULT 0 NOT NULL
);


--
-- Name: TABLE car_models; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.car_models IS 'A list of predefined car models, each owned by a specific car brand from the car_brands table, that a user can select from (to avoid manual entries).';


--
-- Name: car_modification_gallery; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_modification_gallery (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    mod_id uuid NOT NULL,
    url text NOT NULL,
    type text NOT NULL,
    phase text NOT NULL,
    CONSTRAINT chk_phase CHECK ((phase = ANY (ARRAY['before'::text, 'after'::text]))),
    CONSTRAINT chk_type CHECK ((type = ANY (ARRAY['image'::text, 'video'::text])))
);


--
-- Name: TABLE car_modification_gallery; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.car_modification_gallery IS 'Stores before & after images and videos of individual mods.';


--
-- Name: COLUMN car_modification_gallery.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.car_modification_gallery.type IS 'Content type: Image or Video';


--
-- Name: COLUMN car_modification_gallery.phase; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.car_modification_gallery.phase IS 'Whether the modification image / video is ''before'' or ''after''';


--
-- Name: car_modifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_modifications (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    car_id uuid NOT NULL,
    category_id text NOT NULL,
    title text NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT (now() AT TIME ZONE 'utc'::text) NOT NULL,
    installation_date timestamp with time zone NOT NULL,
    price integer,
    mileage_at_install integer,
    CONSTRAINT car_modifications_mileage_at_install_check CHECK ((mileage_at_install > 0)),
    CONSTRAINT car_modifications_price_check CHECK (((price)::double precision > (0.0)::double precision))
);


--
-- Name: TABLE car_modifications; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.car_modifications IS 'Modification history per car. Travels with the car on transfer.';


--
-- Name: COLUMN car_modifications.category_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.car_modifications.category_id IS 'Tells what type of modification is this';


--
-- Name: COLUMN car_modifications.installation_date; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.car_modifications.installation_date IS 'The date when the mod was installed. Can be in the past, but not in the future';


--
-- Name: COLUMN car_modifications.price; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.car_modifications.price IS 'The cost of the mods.';


--
-- Name: COLUMN car_modifications.mileage_at_install; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.car_modifications.mileage_at_install IS 'How many kilometers or miles the car had when installing the mods.';


--
-- Name: car_status_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_status_options (
    id text NOT NULL,
    type text NOT NULL
);


--
-- Name: TABLE car_status_options; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.car_status_options IS 'Different types of cars (eg: Daily Driver, Project Car, Track Toy, etc.)';


--
-- Name: cars; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cars (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    garage_id uuid NOT NULL,
    brand_id uuid NOT NULL,
    model_id uuid NOT NULL,
    drivetrain_id text NOT NULL,
    year integer NOT NULL,
    horsepower integer NOT NULL,
    torque integer NOT NULL,
    weight integer NOT NULL,
    engine_displacement real NOT NULL,
    zero_to_one_hundred real,
    chassis_code text,
    engine_code text,
    created_at timestamp with time zone DEFAULT (now() AT TIME ZONE 'utc'::text) NOT NULL,
    color_id text NOT NULL,
    cover_image_url text,
    mileage_unit_id text NOT NULL,
    status_id text NOT NULL,
    mileage integer,
    model_code text,
    fuel_id text NOT NULL,
    story text,
    license_plate text,
    CONSTRAINT cars_license_plate_check CHECK ((length(license_plate) <= 30)),
    CONSTRAINT cars_mileage_check CHECK ((mileage >= 0))
);


--
-- Name: TABLE cars; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.cars IS 'Individual cars belonging to a garage. Owner derived via garages.owner_id.';


--
-- Name: COLUMN cars.cover_image_url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.cars.cover_image_url IS 'Contains the Cloudflare R2 key for an image';


--
-- Name: COLUMN cars.status_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.cars.status_id IS 'FK to the car_status_options table; describes what type of car this is (eg: daily driver, off-roader, etc)';


--
-- Name: COLUMN cars.mileage; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.cars.mileage IS 'The many units of distance (either kilometers or miles) the car has.';


--
-- Name: COLUMN cars.model_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.cars.model_code IS 'What code the car has (eg: E60 - BMW 5 Series)';


--
-- Name: COLUMN cars.fuel_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.cars.fuel_id IS 'What type of fuel the car uses - FK to car_fuel_type_options';


--
-- Name: COLUMN cars.story; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.cars.story IS '(Optiional) A text describing the car''s story';


--
-- Name: COLUMN cars.license_plate; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.cars.license_plate IS 'A car''s license plate (optional field)';


--
-- Name: cities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cities (
    id text NOT NULL,
    name text NOT NULL,
    region text NOT NULL,
    country text DEFAULT 'RO'::text NOT NULL,
    location public.geography(Point,4326) NOT NULL
);


--
-- Name: TABLE cities; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.cities IS 'Contains a list of predefined cities from individual countries from the ''countries'' table. Not every city from every country will be here.';


--
-- Name: COLUMN cities.location; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.cities.location IS 'The geographical location (long, lat)';


--
-- Name: comment_likes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.comment_likes (
    comment_id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: comment_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.comment_reports (
    comment_id uuid NOT NULL,
    reporter_id uuid NOT NULL,
    reason_id uuid,
    status public.report_status DEFAULT 'pending'::public.report_status,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: comments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.comments (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    post_id uuid NOT NULL,
    user_id uuid NOT NULL,
    content text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    parent_comment_id uuid,
    is_deleted boolean DEFAULT false NOT NULL,
    likes_count integer DEFAULT 0 NOT NULL,
    reply_count integer DEFAULT 0 NOT NULL,
    CONSTRAINT comments_likes_count_check CHECK ((likes_count >= 0)),
    CONSTRAINT comments_reply_count_check CHECK ((reply_count >= 0))
);


--
-- Name: TABLE comments; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.comments IS 'Stores the comments for individual posts from the ''posts'' table.';


--
-- Name: COLUMN comments.parent_comment_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.comments.parent_comment_id IS '(Optional) Allows the creation of comment threads, just like on Reddit. Each comment can have a parent comment until it''s null which means it''s a root comment.';


--
-- Name: COLUMN comments.is_deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.comments.is_deleted IS 'True if comment is deleted, False by default - WARNING: if is_deleted = True, it doesn''t mean that the comment is actually deleted from database. It''ll just be marked as ''deleted'' in mobile';


--
-- Name: COLUMN comments.likes_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.comments.likes_count IS 'Stores how many likes did this comment get.';


--
-- Name: COLUMN comments.reply_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.comments.reply_count IS 'The number of child comments having the current comment as their parent';


--
-- Name: community_role_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.community_role_options (
    id text NOT NULL,
    name text NOT NULL
);


--
-- Name: TABLE community_role_options; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.community_role_options IS 'Configured at onboarding. Can be changed after. Describes what role(s) a user can have in the app - IMPORTANT: not what type of privilleges (eg: moderator, admin) has; that''s a different table';


--
-- Name: countries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.countries (
    id text NOT NULL,
    name text NOT NULL
);


--
-- Name: TABLE countries; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.countries IS 'Contains a list of predefined countries for the users to choose their location. Not every country will be available on purpose.';


--
-- Name: dm_conversations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dm_conversations (
    id uuid NOT NULL,
    user_a uuid NOT NULL,
    user_b uuid NOT NULL,
    last_message_at timestamp with time zone,
    last_message_preview text,
    last_message_sender_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT dm_conversations_pair_ordered CHECK ((user_a < user_b))
);


--
-- Name: TABLE dm_conversations; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.dm_conversations IS '1:1 DM conversations. Canonical pair ordering (user_a < user_b) so one pair = one row. last_message_* is denormalized for cheap chat-list rendering.';


--
-- Name: dm_message_car_tags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dm_message_car_tags (
    message_id uuid NOT NULL,
    car_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: dm_messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dm_messages (
    id uuid NOT NULL,
    conversation_id uuid NOT NULL,
    sender_id uuid NOT NULL,
    content text NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE dm_messages; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.dm_messages IS 'DM messages. Soft delete: is_deleted = true and content blanked (both sides render "deleted message"). created_at is written by the backend so the keyset cursor and dm_conversations.last_message_at agree exactly.';


--
-- Name: dm_participant_state; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dm_participant_state (
    conversation_id uuid NOT NULL,
    user_id uuid NOT NULL,
    unread_count integer DEFAULT 0 NOT NULL,
    last_read_message_id uuid,
    hidden_at timestamp with time zone
);


--
-- Name: TABLE dm_participant_state; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.dm_participant_state IS 'Per-user view of a DM conversation: unread badge count, read receipt watermark (last_read_message_id), and hidden_at ("delete chat" hides it for one side only; unhidden when a new message arrives).';


--
-- Name: dream_cars; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dream_cars (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    profile_id uuid NOT NULL,
    brand_id uuid NOT NULL,
    model_id uuid,
    created_at timestamp with time zone DEFAULT (now() AT TIME ZONE 'utc'::text) NOT NULL
);


--
-- Name: TABLE dream_cars; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.dream_cars IS 'Contains individual car models that the user saved as their dream cars - NOT to be confused with saved cars in the app';


--
-- Name: feedback; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.feedback (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    content text NOT NULL,
    type text NOT NULL,
    feature text,
    reproduction_steps text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    response text,
    status text DEFAULT 'submitted'::text NOT NULL,
    vote_count integer DEFAULT 0 NOT NULL,
    comment_count integer DEFAULT 0 NOT NULL,
    CONSTRAINT feedback_comment_count_check CHECK ((comment_count >= 0)),
    CONSTRAINT feedback_vote_count_check CHECK ((vote_count >= 0))
);


--
-- Name: TABLE feedback; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.feedback IS 'Stores individual feedback messages from the users.';


--
-- Name: COLUMN feedback.response; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.feedback.response IS 'The team''s response to that specific feedback - can be null';


--
-- Name: feedback_comments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.feedback_comments (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    feedback_id uuid NOT NULL,
    user_id uuid NOT NULL,
    content text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT feedback_comments_content_check CHECK ((length(content) <= 2000))
);


--
-- Name: TABLE feedback_comments; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.feedback_comments IS 'User discussion under a feedback entry on the public feedback board.';


--
-- Name: feedback_feature_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.feedback_feature_options (
    id text NOT NULL,
    name text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE feedback_feature_options; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.feedback_feature_options IS '(Reference table) - For what feature do we write a feedback';


--
-- Name: feedback_status_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.feedback_status_options (
    id text NOT NULL,
    name text NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    color text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: feedback_subscriptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.feedback_subscriptions (
    feedback_id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE feedback_subscriptions; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.feedback_subscriptions IS 'Users who opted in to in-app notifications for a feedback''s status changes.';


--
-- Name: feedback_type_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.feedback_type_options (
    id text NOT NULL,
    type text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE feedback_type_options; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.feedback_type_options IS '(Reference table) - What type of feedback category is used (eg: bug, report, feature, etc)';


--
-- Name: feedback_votes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.feedback_votes (
    feedback_id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE feedback_votes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.feedback_votes IS 'One upvote per user per feedback on the public feedback board.';


--
-- Name: follows; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.follows (
    follower_id uuid NOT NULL,
    following_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    status text DEFAULT 'accepted'::text NOT NULL,
    CONSTRAINT follows_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'accepted'::text]))),
    CONSTRAINT no_self_follow CHECK ((follower_id <> following_id))
);


--
-- Name: forum_post_likes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forum_post_likes (
    post_id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: forum_shortcuts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forum_shortcuts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    name text NOT NULL,
    brand_id uuid,
    model_id uuid,
    topic_id text,
    sort_order integer DEFAULT 0 NOT NULL,
    notify boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT forum_shortcuts_check CHECK (((brand_id IS NOT NULL) OR (model_id IS NOT NULL) OR (topic_id IS NOT NULL)))
);


--
-- Name: TABLE forum_shortcuts; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.forum_shortcuts IS 'A saved forum filter. Any combination of brand/model/topic. E.g. (model=M4, topic=tuning).';


--
-- Name: forum_thread_likes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forum_thread_likes (
    thread_id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: forum_thread_reads; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forum_thread_reads (
    user_id uuid NOT NULL,
    thread_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: forum_thread_replies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forum_thread_replies (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    thread_id uuid NOT NULL,
    user_id uuid NOT NULL,
    parent_post_id uuid,
    content text NOT NULL,
    likes_count integer DEFAULT 0 NOT NULL,
    reply_count integer DEFAULT 0 NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT forum_posts_likes_count_check CHECK ((likes_count >= 0)),
    CONSTRAINT forum_posts_reply_count_check CHECK ((reply_count >= 0))
);


--
-- Name: TABLE forum_thread_replies; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.forum_thread_replies IS 'Stores the replies within a thread (similar to comments in posts)';


--
-- Name: COLUMN forum_thread_replies.reply_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.forum_thread_replies.reply_count IS 'Number of direct child replies (parent_post_id = this row). Maintained by trigger.';


--
-- Name: forum_thread_reply_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forum_thread_reply_reports (
    reply_id uuid NOT NULL,
    reporter_id uuid NOT NULL,
    reason_id uuid,
    status public.report_status DEFAULT 'pending'::public.report_status,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: forum_thread_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forum_thread_reports (
    thread_id uuid NOT NULL,
    reporter_id uuid NOT NULL,
    reason_id uuid,
    status public.report_status DEFAULT 'pending'::public.report_status,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: forum_thread_saves; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forum_thread_saves (
    thread_id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: forum_thread_topics; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forum_thread_topics (
    thread_id uuid NOT NULL,
    topic_id text NOT NULL
);


--
-- Name: TABLE forum_thread_topics; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.forum_thread_topics IS 'Join table between forum_threads and forum_topics - describes what topics are marked for a thread (predefined by the thread''s author)';


--
-- Name: forum_threads; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forum_threads (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    title text NOT NULL,
    content text,
    brand_id uuid,
    model_id uuid,
    likes_count integer DEFAULT 0 NOT NULL,
    reply_count integer DEFAULT 0 NOT NULL,
    ranking_score double precision DEFAULT 0 NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    is_locked boolean DEFAULT false NOT NULL,
    is_pinned boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    last_activity_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT forum_threads_likes_count_check CHECK ((likes_count >= 0)),
    CONSTRAINT forum_threads_reply_count_check CHECK ((reply_count >= 0)),
    CONSTRAINT forum_threads_title_check CHECK ((length(title) <= 200))
);


--
-- Name: TABLE forum_threads; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.forum_threads IS 'Single pool of forum threads. Reachable via car axis (brand/model) or topic axis (forum_thread_topics). No content duplication.';


--
-- Name: COLUMN forum_threads.brand_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.forum_threads.brand_id IS 'Auto-filled from model_id when a model is set (trg_forum_threads_set_brand). Set directly for brand-level threads.';


--
-- Name: forum_topics; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forum_topics (
    id text NOT NULL,
    name text NOT NULL,
    kind text DEFAULT 'format'::text NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    color text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    thread_count integer DEFAULT 0 NOT NULL,
    CONSTRAINT forum_topics_kind_check CHECK ((kind = ANY (ARRAY['component'::text, 'format'::text])))
);


--
-- Name: TABLE forum_topics; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.forum_topics IS 'Topic axis for forums (tuning, suspension, DIY...). kind groups the UI: component vs format.';


--
-- Name: garages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.garages (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    owner_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT (now() AT TIME ZONE 'utc'::text) NOT NULL
);


--
-- Name: TABLE garages; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.garages IS 'Container for a user''s cars. One garage per user (UNIQUE on owner_id).';


--
-- Name: moderation_actions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.moderation_actions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    case_id bigint,
    moderator_id uuid,
    action text NOT NULL,
    target_type text NOT NULL,
    target_id uuid NOT NULL,
    target_author_id uuid,
    content_snapshot text,
    note text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT moderation_actions_action_check CHECK ((action = ANY (ARRAY['approve'::text, 'remove_content'::text, 'warn'::text, 'ban'::text, 'unban'::text, 'escalate'::text]))),
    CONSTRAINT moderation_actions_target_type_check CHECK ((target_type = ANY (ARRAY['post'::text, 'comment'::text, 'profile'::text, 'forum_thread'::text, 'forum_thread_reply'::text])))
);


--
-- Name: TABLE moderation_actions; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.moderation_actions IS 'Audit trail of moderator actions. content_snapshot preserves removed text (content itself is hard-deleted). Prior warnings / removals per author are derived by counting rows here.';


--
-- Name: moderation_cases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.moderation_cases (
    id bigint NOT NULL,
    target_type text NOT NULL,
    target_id uuid NOT NULL,
    status text DEFAULT 'open'::text NOT NULL,
    resolution text,
    resolved_by uuid,
    resolved_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    last_reported_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT moderation_cases_resolution_check CHECK ((resolution = ANY (ARRAY['approved'::text, 'content_removed'::text, 'author_warned'::text, 'author_banned'::text, 'content_deleted'::text]))),
    CONSTRAINT moderation_cases_status_check CHECK ((status = ANY (ARRAY['open'::text, 'escalated'::text, 'resolved'::text]))),
    CONSTRAINT moderation_cases_target_type_check CHECK ((target_type = ANY (ARRAY['post'::text, 'comment'::text, 'profile'::text, 'forum_thread'::text, 'forum_thread_reply'::text])))
);


--
-- Name: TABLE moderation_cases; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.moderation_cases IS 'Admin-dashboard moderation queue: groups all reports against one target. No FK on target_id (targets live in different tables and may be hard-deleted); a resolved case reopens if the target is reported again.';


--
-- Name: moderation_cases_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.moderation_cases ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.moderation_cases_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: notification_preferences; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_preferences (
    profile_id uuid NOT NULL,
    likes_enabled boolean DEFAULT true NOT NULL,
    comments_enabled boolean DEFAULT true NOT NULL,
    shares_enabled boolean DEFAULT true NOT NULL,
    dms_enabled boolean DEFAULT true NOT NULL,
    flash_meets_enabled boolean DEFAULT true NOT NULL,
    price_drops_enabled boolean DEFAULT true NOT NULL,
    updated_at timestamp with time zone DEFAULT (now() AT TIME ZONE 'utc'::text) NOT NULL,
    organized_events_enabled boolean NOT NULL
);


--
-- Name: TABLE notification_preferences; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.notification_preferences IS 'Describes what type of notifications are turned on by the user.';


--
-- Name: COLUMN notification_preferences.flash_meets_enabled; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.notification_preferences.flash_meets_enabled IS 'Whether the user receives notifications about flash meets';


--
-- Name: COLUMN notification_preferences.organized_events_enabled; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.notification_preferences.organized_events_enabled IS 'Wether the user receives notifications about organized events.';


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    type text NOT NULL,
    title text NOT NULL,
    body text,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    is_read boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE notifications; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.notifications IS 'Generic in-app notifications (no push). type discriminates the producer (feedback_status, ticket_reply, moderation_warning, content_removed, ...); payload carries type-specific ids for deep-linking.';


--
-- Name: post_images; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.post_images (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    post_id uuid NOT NULL,
    image_key text NOT NULL,
    display_order smallint DEFAULT '0'::smallint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE post_images; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.post_images IS 'Contains a post''s images stored as R2 keys (not entire URLs - Spring will take care to build and return a fully working URL). Each row is an image belonging to a single post. Multiple images for a post = multiple rows with the same post_id';


--
-- Name: COLUMN post_images.image_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.post_images.image_key IS 'Contains only the Cloudflare''s R2 key - NOT the entire URL. Backend will construct the full URL and send it to the mobile client.';


--
-- Name: post_likes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.post_likes (
    post_id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: post_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.post_reports (
    post_id uuid NOT NULL,
    reporter_id uuid NOT NULL,
    reason_id uuid,
    status public.report_status DEFAULT 'pending'::public.report_status,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: post_shares; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.post_shares (
    post_id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    content text
);


--
-- Name: COLUMN post_shares.content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.post_shares.content IS '(Optional) If the user wants to add his thoughts on someone else''s post (just like on Facebook)';


--
-- Name: posts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.posts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    description text NOT NULL,
    user_id uuid NOT NULL,
    likes_count_enabled boolean DEFAULT true NOT NULL,
    comments_count_enabled boolean DEFAULT true NOT NULL,
    shares_count_enabled boolean DEFAULT true NOT NULL,
    likes_count bigint DEFAULT '0'::bigint NOT NULL,
    comments_count bigint DEFAULT '0'::bigint NOT NULL,
    shares_count bigint DEFAULT '0'::bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    ranking_score double precision DEFAULT '0'::double precision NOT NULL,
    quote_shares_count bigint DEFAULT '0'::bigint NOT NULL,
    saved_count bigint DEFAULT '0'::bigint NOT NULL,
    saved_count_enabled boolean DEFAULT true NOT NULL,
    CONSTRAINT posts_saved_count_check CHECK ((saved_count >= 0))
);


--
-- Name: TABLE posts; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.posts IS 'Contains the posts of each user within the app. These posts will be made public in the app''s Feed.';


--
-- Name: COLUMN posts.ranking_score; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.posts.ranking_score IS 'The ranking score of the post. It is used in the feed to show the posts with the highest score (most viral); DESC order';


--
-- Name: COLUMN posts.quote_shares_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.posts.quote_shares_count IS 'How many shares on this post with a description attached by the user (eg: description not null)';


--
-- Name: COLUMN posts.saved_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.posts.saved_count IS 'The number of times a post was saved.';


--
-- Name: COLUMN posts.saved_count_enabled; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.posts.saved_count_enabled IS 'Whether the number of saves on a post is visible for other users or not.';


--
-- Name: price_currencies_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.price_currencies_options (
    id text NOT NULL,
    name text NOT NULL
);


--
-- Name: TABLE price_currencies_options; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.price_currencies_options IS 'A predefined set of currencies. Used when adding a new mod with its price (for example)';


--
-- Name: profile_car_categories_junction; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.profile_car_categories_junction (
    profile_id uuid NOT NULL,
    category_id text NOT NULL
);


--
-- Name: TABLE profile_car_categories_junction; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.profile_car_categories_junction IS 'When the user onboards in the app, they can choose their favorite car brands - this is why this table exists.';


--
-- Name: profile_community_roles_junction; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.profile_community_roles_junction (
    profile_id uuid NOT NULL,
    role_id text NOT NULL
);


--
-- Name: TABLE profile_community_roles_junction; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.profile_community_roles_junction IS 'The join table between a user and their role(s) in the app';


--
-- Name: profile_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.profile_reports (
    profile_id uuid NOT NULL,
    reporter_id uuid NOT NULL,
    reason_id uuid,
    status public.report_status DEFAULT 'pending'::public.report_status,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.profiles (
    id uuid NOT NULL,
    name text,
    updated_at timestamp with time zone DEFAULT (now() AT TIME ZONE 'utc'::text) NOT NULL,
    avatar_url text,
    username text,
    followers_count bigint DEFAULT '0'::bigint NOT NULL,
    following_count bigint DEFAULT '0'::bigint NOT NULL,
    bio text DEFAULT ''::text NOT NULL,
    is_verified boolean DEFAULT false NOT NULL,
    is_business boolean DEFAULT false NOT NULL,
    external_link text DEFAULT ''::text NOT NULL,
    role text DEFAULT 'user'::text NOT NULL,
    requires_onboarding boolean DEFAULT true NOT NULL,
    city_id text,
    discovery_radius_km integer DEFAULT 10,
    realtime_location public.geography,
    is_banned boolean DEFAULT false NOT NULL,
    banned_until timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    app_language text DEFAULT 'en'::text NOT NULL,
    CONSTRAINT profiles_bio_check CHECK ((length(bio) <= 500)),
    CONSTRAINT profiles_discovery_radius_km_check CHECK (((discovery_radius_km >= 1) AND (discovery_radius_km <= 100))),
    CONSTRAINT profiles_followers_count_check CHECK ((followers_count >= 0)),
    CONSTRAINT profiles_following_count_check CHECK ((following_count >= 0))
);


--
-- Name: COLUMN profiles.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.profiles.updated_at IS 'Tells us when was the last time the user updated their profile';


--
-- Name: COLUMN profiles.avatar_url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.profiles.avatar_url IS 'Profile URL picture of the user';


--
-- Name: COLUMN profiles.username; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.profiles.username IS 'The unique username associated with each user in the app';


--
-- Name: COLUMN profiles.followers_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.profiles.followers_count IS 'The total number of people who are following this user';


--
-- Name: COLUMN profiles.following_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.profiles.following_count IS 'The total number of people this user follows';


--
-- Name: COLUMN profiles.bio; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.profiles.bio IS 'The bio section of the profile, just like on Instagram.';


--
-- Name: COLUMN profiles.is_verified; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.profiles.is_verified IS 'Used to verify if this account has a blue checkmark. By default, it''s set to false';


--
-- Name: COLUMN profiles.is_business; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.profiles.is_business IS 'Used to verify if an account is a business account (eg: a company)';


--
-- Name: COLUMN profiles.external_link; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.profiles.external_link IS '(optional) A single external link for the users to put whatever link feels relevant to them.';


--
-- Name: COLUMN profiles.requires_onboarding; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.profiles.requires_onboarding IS 'true -> the user needs to go through the onboarding process inside the app; false -> the user will skip the onboarding process';


--
-- Name: COLUMN profiles.realtime_location; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.profiles.realtime_location IS 'Contains the user''s realtime location (if location services is gained access)';


--
-- Name: COLUMN profiles.is_banned; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.profiles.is_banned IS 'Set by a moderator ban. Enforced by a backend request interceptor; banned_until null = permanent.';


--
-- Name: COLUMN profiles.banned_until; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.profiles.banned_until IS 'Temp-ban expiry. Only meaningful while is_banned is true.';


--
-- Name: COLUMN profiles.app_language; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.profiles.app_language IS 'What language is the user''s app set to.';


--
-- Name: report_reasons; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.report_reasons (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    target public.target_entity NOT NULL,
    reason text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE report_reasons; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.report_reasons IS 'Contains categories of reasons for a specific type of report (eg: profile / comment / post)';


--
-- Name: saved_posts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.saved_posts (
    post_id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: support_ticket_categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.support_ticket_categories (
    id text NOT NULL,
    name text NOT NULL,
    sort_order integer NOT NULL
);


--
-- Name: TABLE support_ticket_categories; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.support_ticket_categories IS '(Reference table) - Categories a user picks when opening a support ticket.';


--
-- Name: support_ticket_messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.support_ticket_messages (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    ticket_id uuid NOT NULL,
    sender_id uuid NOT NULL,
    is_staff boolean DEFAULT false NOT NULL,
    content text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT support_ticket_messages_content_check CHECK ((length(content) <= 5000))
);


--
-- Name: TABLE support_ticket_messages; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.support_ticket_messages IS 'Conversation thread of a support ticket, oldest first.';


--
-- Name: support_tickets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.support_tickets (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    subject text NOT NULL,
    category text NOT NULL,
    priority text DEFAULT 'normal'::text NOT NULL,
    status text DEFAULT 'open'::text NOT NULL,
    assigned_to uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    last_message_at timestamp with time zone DEFAULT now() NOT NULL,
    resolved_at timestamp with time zone,
    CONSTRAINT support_tickets_priority_check CHECK ((priority = ANY (ARRAY['low'::text, 'normal'::text, 'high'::text, 'urgent'::text]))),
    CONSTRAINT support_tickets_status_check CHECK ((status = ANY (ARRAY['open'::text, 'awaiting_user'::text, 'resolved'::text]))),
    CONSTRAINT support_tickets_subject_check CHECK ((length(subject) <= 200))
);


--
-- Name: TABLE support_tickets; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.support_tickets IS 'Support tickets from users and business accounts. status: open = waiting on staff, awaiting_user = staff replied, resolved = closed. Priority is admin-set.';


--
-- Name: COLUMN support_tickets.resolved_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.support_tickets.resolved_at IS 'Set by the backend when status moves to resolved; cleared on reopen. Backs the "resolved today" dashboard stat.';


--
-- Name: tagged_cars; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tagged_cars (
    post_id uuid NOT NULL,
    car_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: tagged_people; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tagged_people (
    post_id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: user_presence; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_presence (
    user_id uuid NOT NULL,
    last_seen_at timestamp with time zone NOT NULL
);


--
-- Name: admin_team_members admin_team_members_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_team_members
    ADD CONSTRAINT admin_team_members_email_key UNIQUE (email);


--
-- Name: admin_team_members admin_team_members_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_team_members
    ADD CONSTRAINT admin_team_members_pkey PRIMARY KEY (user_id);


--
-- Name: app_language_options app_language_options_language_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_language_options
    ADD CONSTRAINT app_language_options_language_key UNIQUE (language);


--
-- Name: app_language_options app_language_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_language_options
    ADD CONSTRAINT app_language_options_pkey PRIMARY KEY (id);


--
-- Name: blocked_accounts blocked_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blocked_accounts
    ADD CONSTRAINT blocked_accounts_pkey PRIMARY KEY (blocker_id, blocked_id);


--
-- Name: car_brands car_brands_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_brands
    ADD CONSTRAINT car_brands_pkey PRIMARY KEY (id);


--
-- Name: car_category_options car_category_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_category_options
    ADD CONSTRAINT car_category_options_pkey PRIMARY KEY (id);


--
-- Name: car_color_options car_color_options_color_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_color_options
    ADD CONSTRAINT car_color_options_color_code_key UNIQUE (color_code);


--
-- Name: car_color_options car_color_options_color_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_color_options
    ADD CONSTRAINT car_color_options_color_key UNIQUE (name);


--
-- Name: car_color_options car_color_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_color_options
    ADD CONSTRAINT car_color_options_pkey PRIMARY KEY (id);


--
-- Name: car_distance_units car_distance_units_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_distance_units
    ADD CONSTRAINT car_distance_units_name_key UNIQUE (name);


--
-- Name: car_distance_units car_distance_units_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_distance_units
    ADD CONSTRAINT car_distance_units_pkey PRIMARY KEY (id);


--
-- Name: car_fuel_type_options car_fuel_type_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_fuel_type_options
    ADD CONSTRAINT car_fuel_type_options_pkey PRIMARY KEY (id);


--
-- Name: car_gallery car_gallery_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_gallery
    ADD CONSTRAINT car_gallery_pkey PRIMARY KEY (id);


--
-- Name: car_mod_categories car_mod_categories_mod_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_mod_categories
    ADD CONSTRAINT car_mod_categories_mod_name_key UNIQUE (mod_name);


--
-- Name: car_mod_categories car_mod_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_mod_categories
    ADD CONSTRAINT car_mod_categories_pkey PRIMARY KEY (id);


--
-- Name: car_models car_models_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_models
    ADD CONSTRAINT car_models_pkey PRIMARY KEY (id);


--
-- Name: car_modification_gallery car_modification_gallery_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_modification_gallery
    ADD CONSTRAINT car_modification_gallery_pkey PRIMARY KEY (id);


--
-- Name: car_modifications car_modifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_modifications
    ADD CONSTRAINT car_modifications_pkey PRIMARY KEY (id);


--
-- Name: car_status_options car_status_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_status_options
    ADD CONSTRAINT car_status_options_pkey PRIMARY KEY (id);


--
-- Name: car_status_options car_status_options_type_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_status_options
    ADD CONSTRAINT car_status_options_type_key UNIQUE (type);


--
-- Name: cars cars_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cars
    ADD CONSTRAINT cars_pkey PRIMARY KEY (id);


--
-- Name: cities cities_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cities
    ADD CONSTRAINT cities_pkey PRIMARY KEY (id);


--
-- Name: comment_likes comment_likes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comment_likes
    ADD CONSTRAINT comment_likes_pkey PRIMARY KEY (comment_id, user_id);


--
-- Name: comment_reports comment_reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comment_reports
    ADD CONSTRAINT comment_reports_pkey PRIMARY KEY (comment_id, reporter_id);


--
-- Name: comments comments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comments
    ADD CONSTRAINT comments_pkey PRIMARY KEY (id);


--
-- Name: community_role_options community_role_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.community_role_options
    ADD CONSTRAINT community_role_options_pkey PRIMARY KEY (id);


--
-- Name: countries countries_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.countries
    ADD CONSTRAINT countries_name_key UNIQUE (name);


--
-- Name: countries countries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.countries
    ADD CONSTRAINT countries_pkey PRIMARY KEY (id);


--
-- Name: dm_conversations dm_conversations_pair_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dm_conversations
    ADD CONSTRAINT dm_conversations_pair_unique UNIQUE (user_a, user_b);


--
-- Name: dm_conversations dm_conversations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dm_conversations
    ADD CONSTRAINT dm_conversations_pkey PRIMARY KEY (id);


--
-- Name: dm_message_car_tags dm_message_car_tags_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dm_message_car_tags
    ADD CONSTRAINT dm_message_car_tags_pkey PRIMARY KEY (message_id, car_id);


--
-- Name: dm_messages dm_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dm_messages
    ADD CONSTRAINT dm_messages_pkey PRIMARY KEY (id);


--
-- Name: dm_participant_state dm_participant_state_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dm_participant_state
    ADD CONSTRAINT dm_participant_state_pkey PRIMARY KEY (conversation_id, user_id);


--
-- Name: dream_cars dream_cars_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dream_cars
    ADD CONSTRAINT dream_cars_pkey PRIMARY KEY (id);


--
-- Name: car_drivetrain_options drivetrain_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_drivetrain_options
    ADD CONSTRAINT drivetrain_options_pkey PRIMARY KEY (id);


--
-- Name: feedback_comments feedback_comments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_comments
    ADD CONSTRAINT feedback_comments_pkey PRIMARY KEY (id);


--
-- Name: feedback_feature_options feedback_feature_options_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_feature_options
    ADD CONSTRAINT feedback_feature_options_name_key UNIQUE (name);


--
-- Name: feedback_feature_options feedback_feature_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_feature_options
    ADD CONSTRAINT feedback_feature_options_pkey PRIMARY KEY (id);


--
-- Name: feedback feedback_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback
    ADD CONSTRAINT feedback_pkey PRIMARY KEY (id);


--
-- Name: feedback_status_options feedback_status_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_status_options
    ADD CONSTRAINT feedback_status_options_pkey PRIMARY KEY (id);


--
-- Name: feedback_subscriptions feedback_subscriptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_subscriptions
    ADD CONSTRAINT feedback_subscriptions_pkey PRIMARY KEY (feedback_id, user_id);


--
-- Name: feedback_type_options feedback_type_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_type_options
    ADD CONSTRAINT feedback_type_options_pkey PRIMARY KEY (id);


--
-- Name: feedback_votes feedback_votes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_votes
    ADD CONSTRAINT feedback_votes_pkey PRIMARY KEY (feedback_id, user_id);


--
-- Name: follows follows_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.follows
    ADD CONSTRAINT follows_pkey PRIMARY KEY (follower_id, following_id);


--
-- Name: forum_post_likes forum_post_likes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_post_likes
    ADD CONSTRAINT forum_post_likes_pkey PRIMARY KEY (post_id, user_id);


--
-- Name: forum_thread_replies forum_posts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_replies
    ADD CONSTRAINT forum_posts_pkey PRIMARY KEY (id);


--
-- Name: forum_shortcuts forum_shortcuts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_shortcuts
    ADD CONSTRAINT forum_shortcuts_pkey PRIMARY KEY (id);


--
-- Name: forum_thread_likes forum_thread_likes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_likes
    ADD CONSTRAINT forum_thread_likes_pkey PRIMARY KEY (thread_id, user_id);


--
-- Name: forum_thread_reads forum_thread_reads_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_reads
    ADD CONSTRAINT forum_thread_reads_pkey PRIMARY KEY (user_id, thread_id);


--
-- Name: forum_thread_reply_reports forum_thread_reply_reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_reply_reports
    ADD CONSTRAINT forum_thread_reply_reports_pkey PRIMARY KEY (reply_id, reporter_id);


--
-- Name: forum_thread_reports forum_thread_reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_reports
    ADD CONSTRAINT forum_thread_reports_pkey PRIMARY KEY (thread_id, reporter_id);


--
-- Name: forum_thread_saves forum_thread_saves_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_saves
    ADD CONSTRAINT forum_thread_saves_pkey PRIMARY KEY (thread_id, user_id);


--
-- Name: forum_thread_topics forum_thread_topics_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_topics
    ADD CONSTRAINT forum_thread_topics_pkey PRIMARY KEY (thread_id, topic_id);


--
-- Name: forum_threads forum_threads_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_threads
    ADD CONSTRAINT forum_threads_pkey PRIMARY KEY (id);


--
-- Name: forum_topics forum_topics_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_topics
    ADD CONSTRAINT forum_topics_pkey PRIMARY KEY (id);


--
-- Name: garages garages_owner_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.garages
    ADD CONSTRAINT garages_owner_id_key UNIQUE (owner_id);


--
-- Name: garages garages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.garages
    ADD CONSTRAINT garages_pkey PRIMARY KEY (id);


--
-- Name: moderation_actions moderation_actions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.moderation_actions
    ADD CONSTRAINT moderation_actions_pkey PRIMARY KEY (id);


--
-- Name: moderation_cases moderation_cases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.moderation_cases
    ADD CONSTRAINT moderation_cases_pkey PRIMARY KEY (id);


--
-- Name: moderation_cases moderation_cases_target_type_target_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.moderation_cases
    ADD CONSTRAINT moderation_cases_target_type_target_id_key UNIQUE (target_type, target_id);


--
-- Name: notification_preferences notification_preferences_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_preferences
    ADD CONSTRAINT notification_preferences_pkey PRIMARY KEY (profile_id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: post_images post_images_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_images
    ADD CONSTRAINT post_images_pkey PRIMARY KEY (id);


--
-- Name: post_likes post_likes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_likes
    ADD CONSTRAINT post_likes_pkey PRIMARY KEY (post_id, user_id);


--
-- Name: post_reports post_reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_reports
    ADD CONSTRAINT post_reports_pkey PRIMARY KEY (post_id, reporter_id);


--
-- Name: post_shares post_shares_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_shares
    ADD CONSTRAINT post_shares_pkey PRIMARY KEY (post_id, user_id);


--
-- Name: posts posts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.posts
    ADD CONSTRAINT posts_pkey PRIMARY KEY (id);


--
-- Name: price_currencies_options price_currencies_options_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.price_currencies_options
    ADD CONSTRAINT price_currencies_options_name_key UNIQUE (name);


--
-- Name: price_currencies_options price_currencies_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.price_currencies_options
    ADD CONSTRAINT price_currencies_options_pkey PRIMARY KEY (id);


--
-- Name: profile_car_categories_junction profile_car_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profile_car_categories_junction
    ADD CONSTRAINT profile_car_categories_pkey PRIMARY KEY (profile_id, category_id);


--
-- Name: profile_community_roles_junction profile_community_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profile_community_roles_junction
    ADD CONSTRAINT profile_community_roles_pkey PRIMARY KEY (profile_id, role_id);


--
-- Name: profile_reports profile_reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profile_reports
    ADD CONSTRAINT profile_reports_pkey PRIMARY KEY (profile_id, reporter_id);


--
-- Name: profiles profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profiles
    ADD CONSTRAINT profiles_pkey PRIMARY KEY (id);


--
-- Name: profiles profiles_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profiles
    ADD CONSTRAINT profiles_username_key UNIQUE (username);


--
-- Name: report_reasons report_reasons_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_reasons
    ADD CONSTRAINT report_reasons_pkey PRIMARY KEY (id);


--
-- Name: saved_posts saved_posts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.saved_posts
    ADD CONSTRAINT saved_posts_pkey PRIMARY KEY (user_id, post_id);


--
-- Name: support_ticket_categories support_ticket_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_ticket_categories
    ADD CONSTRAINT support_ticket_categories_pkey PRIMARY KEY (id);


--
-- Name: support_ticket_messages support_ticket_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_ticket_messages
    ADD CONSTRAINT support_ticket_messages_pkey PRIMARY KEY (id);


--
-- Name: support_tickets support_tickets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_tickets
    ADD CONSTRAINT support_tickets_pkey PRIMARY KEY (id);


--
-- Name: tagged_cars tagged_cars_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tagged_cars
    ADD CONSTRAINT tagged_cars_pkey PRIMARY KEY (post_id, car_id);


--
-- Name: tagged_people tagged_people_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tagged_people
    ADD CONSTRAINT tagged_people_pkey PRIMARY KEY (post_id, user_id);


--
-- Name: post_images unique_post_display_order; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_images
    ADD CONSTRAINT unique_post_display_order UNIQUE (post_id, display_order);


--
-- Name: user_presence user_presence_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_presence
    ADD CONSTRAINT user_presence_pkey PRIMARY KEY (user_id);


--
-- Name: dm_messages_keyset_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX dm_messages_keyset_idx ON public.dm_messages USING btree (conversation_id, created_at DESC, id DESC);


--
-- Name: dm_participant_state_user_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX dm_participant_state_user_idx ON public.dm_participant_state USING btree (user_id);


--
-- Name: dream_cars_profile_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX dream_cars_profile_id_idx ON public.dream_cars USING btree (profile_id);


--
-- Name: feedback_comments_feedback_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX feedback_comments_feedback_idx ON public.feedback_comments USING btree (feedback_id, created_at DESC);


--
-- Name: feedback_subscriptions_user_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX feedback_subscriptions_user_idx ON public.feedback_subscriptions USING btree (user_id);


--
-- Name: feedback_votes_recent_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX feedback_votes_recent_idx ON public.feedback_votes USING btree (feedback_id, created_at);


--
-- Name: idx_car_models_brand_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_car_models_brand_id ON public.car_models USING btree (brand_id);


--
-- Name: idx_car_modifications_car_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_car_modifications_car_id ON public.car_modifications USING btree (car_id);


--
-- Name: idx_car_modifications_category_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_car_modifications_category_id ON public.car_modifications USING btree (category_id);


--
-- Name: idx_cars_brand_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cars_brand_id ON public.cars USING btree (brand_id);


--
-- Name: idx_cars_color_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cars_color_id ON public.cars USING btree (color_id);


--
-- Name: idx_cars_drivetrain_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cars_drivetrain_id ON public.cars USING btree (drivetrain_id);


--
-- Name: idx_cars_garage_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cars_garage_id ON public.cars USING btree (garage_id);


--
-- Name: idx_cars_mileage_unit_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cars_mileage_unit_id ON public.cars USING btree (mileage_unit_id);


--
-- Name: idx_cars_model_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cars_model_id ON public.cars USING btree (model_id);


--
-- Name: idx_comment_likes_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comment_likes_user_id ON public.comment_likes USING btree (user_id);


--
-- Name: idx_comments_parent_comment_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comments_parent_comment_id ON public.comments USING btree (parent_comment_id);


--
-- Name: idx_comments_post_id_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comments_post_id_created_at ON public.comments USING btree (post_id, created_at DESC);


--
-- Name: idx_comments_post_keyset; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comments_post_keyset ON public.comments USING btree (post_id, created_at DESC, id DESC) WHERE (parent_comment_id IS NULL);


--
-- Name: idx_comments_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comments_user_id ON public.comments USING btree (user_id);


--
-- Name: idx_dm_message_car_tags_car; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dm_message_car_tags_car ON public.dm_message_car_tags USING btree (car_id);


--
-- Name: idx_follows_following_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_follows_following_id ON public.follows USING btree (following_id);


--
-- Name: idx_follows_pending_for_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_follows_pending_for_user ON public.follows USING btree (following_id) WHERE (status = 'pending'::text);


--
-- Name: idx_forum_posts_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_forum_posts_parent ON public.forum_thread_replies USING btree (parent_post_id) WHERE (parent_post_id IS NOT NULL);


--
-- Name: idx_forum_posts_thread; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_forum_posts_thread ON public.forum_thread_replies USING btree (thread_id, created_at);


--
-- Name: idx_forum_posts_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_forum_posts_user ON public.forum_thread_replies USING btree (user_id);


--
-- Name: idx_forum_shortcuts_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_forum_shortcuts_user ON public.forum_shortcuts USING btree (user_id, sort_order);


--
-- Name: idx_forum_thread_saves_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_forum_thread_saves_user ON public.forum_thread_saves USING btree (user_id, created_at DESC, thread_id DESC);


--
-- Name: idx_forum_thread_topics_topic; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_forum_thread_topics_topic ON public.forum_thread_topics USING btree (topic_id, thread_id);


--
-- Name: idx_forum_threads_activity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_forum_threads_activity ON public.forum_threads USING btree (last_activity_at DESC, id DESC);


--
-- Name: idx_forum_threads_brand_ranking; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_forum_threads_brand_ranking ON public.forum_threads USING btree (brand_id, ranking_score DESC, id DESC) WHERE (brand_id IS NOT NULL);


--
-- Name: idx_forum_threads_model_ranking; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_forum_threads_model_ranking ON public.forum_threads USING btree (model_id, ranking_score DESC, id DESC) WHERE (model_id IS NOT NULL);


--
-- Name: idx_forum_threads_ranking; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_forum_threads_ranking ON public.forum_threads USING btree (ranking_score DESC, id DESC);


--
-- Name: idx_forum_threads_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_forum_threads_user ON public.forum_threads USING btree (user_id);


--
-- Name: idx_post_images_post_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_images_post_id ON public.post_images USING btree (post_id, display_order);


--
-- Name: idx_post_likes_post_keyset; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_likes_post_keyset ON public.post_likes USING btree (post_id, created_at DESC, user_id DESC);


--
-- Name: idx_post_likes_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_likes_user_id ON public.post_likes USING btree (user_id);


--
-- Name: idx_post_shares_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_shares_user_id ON public.post_shares USING btree (user_id, created_at DESC);


--
-- Name: idx_posts_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_posts_created_at ON public.posts USING btree (created_at DESC);


--
-- Name: idx_posts_ranking_keyset; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_posts_ranking_keyset ON public.posts USING btree (ranking_score DESC, id DESC);


--
-- Name: idx_posts_user_keyset; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_posts_user_keyset ON public.posts USING btree (user_id, created_at DESC, id DESC);


--
-- Name: idx_profiles_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_profiles_created_at ON public.profiles USING btree (created_at);


--
-- Name: idx_saved_posts_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_saved_posts_user_id ON public.saved_posts USING btree (user_id, created_at DESC);


--
-- Name: idx_tagged_cars_car_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tagged_cars_car_id ON public.tagged_cars USING btree (car_id);


--
-- Name: idx_tagged_people_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tagged_people_user_id ON public.tagged_people USING btree (user_id);


--
-- Name: moderation_actions_author_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX moderation_actions_author_idx ON public.moderation_actions USING btree (target_author_id, action);


--
-- Name: moderation_actions_case_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX moderation_actions_case_idx ON public.moderation_actions USING btree (case_id);


--
-- Name: moderation_cases_queue_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX moderation_cases_queue_idx ON public.moderation_cases USING btree (status, last_reported_at DESC);


--
-- Name: notifications_user_created_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX notifications_user_created_idx ON public.notifications USING btree (user_id, created_at DESC, id DESC);


--
-- Name: notifications_user_unread_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX notifications_user_unread_idx ON public.notifications USING btree (user_id) WHERE (NOT is_read);


--
-- Name: support_ticket_messages_ticket_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX support_ticket_messages_ticket_idx ON public.support_ticket_messages USING btree (ticket_id, created_at);


--
-- Name: support_tickets_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX support_tickets_status_idx ON public.support_tickets USING btree (status, last_message_at DESC);


--
-- Name: support_tickets_user_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX support_tickets_user_idx ON public.support_tickets USING btree (user_id, last_message_at DESC);


--
-- Name: comment_reports comment_reports_case; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER comment_reports_case AFTER INSERT ON public.comment_reports FOR EACH ROW EXECUTE FUNCTION public.upsert_moderation_case('comment', 'comment_id');


--
-- Name: feedback_comments feedback_comments_count; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER feedback_comments_count AFTER INSERT OR DELETE ON public.feedback_comments FOR EACH ROW EXECUTE FUNCTION public.bump_feedback_comment_count();


--
-- Name: feedback_votes feedback_votes_count; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER feedback_votes_count AFTER INSERT OR DELETE ON public.feedback_votes FOR EACH ROW EXECUTE FUNCTION public.bump_feedback_vote_count();


--
-- Name: follows on_follow_change; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER on_follow_change AFTER INSERT OR DELETE OR UPDATE ON public.follows FOR EACH ROW EXECUTE FUNCTION public.handle_follow_change();


--
-- Name: profiles on_profile_created_create_garage; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER on_profile_created_create_garage AFTER INSERT ON public.profiles FOR EACH ROW EXECUTE FUNCTION public.handle_new_profile_garage();


--
-- Name: post_reports post_reports_case; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER post_reports_case AFTER INSERT ON public.post_reports FOR EACH ROW EXECUTE FUNCTION public.upsert_moderation_case('post', 'post_id');


--
-- Name: profile_reports profile_reports_case; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER profile_reports_case AFTER INSERT ON public.profile_reports FOR EACH ROW EXECUTE FUNCTION public.upsert_moderation_case('profile', 'profile_id');


--
-- Name: forum_thread_reply_reports reply_reports_case; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER reply_reports_case AFTER INSERT ON public.forum_thread_reply_reports FOR EACH ROW EXECUTE FUNCTION public.upsert_moderation_case('forum_thread_reply', 'reply_id');


--
-- Name: comments resolve_moderation_cases_on_comment_delete; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER resolve_moderation_cases_on_comment_delete AFTER DELETE ON public.comments FOR EACH ROW EXECUTE FUNCTION public.resolve_moderation_cases_for_deleted_target('comment');


--
-- Name: forum_threads resolve_moderation_cases_on_forum_thread_delete; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER resolve_moderation_cases_on_forum_thread_delete AFTER DELETE ON public.forum_threads FOR EACH ROW EXECUTE FUNCTION public.resolve_moderation_cases_for_deleted_target('forum_thread');


--
-- Name: forum_thread_replies resolve_moderation_cases_on_forum_thread_reply_delete; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER resolve_moderation_cases_on_forum_thread_reply_delete AFTER DELETE ON public.forum_thread_replies FOR EACH ROW EXECUTE FUNCTION public.resolve_moderation_cases_for_deleted_target('forum_thread_reply');


--
-- Name: posts resolve_moderation_cases_on_post_delete; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER resolve_moderation_cases_on_post_delete AFTER DELETE ON public.posts FOR EACH ROW EXECUTE FUNCTION public.resolve_moderation_cases_for_deleted_target('post');


--
-- Name: profiles resolve_moderation_cases_on_profile_delete; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER resolve_moderation_cases_on_profile_delete AFTER DELETE ON public.profiles FOR EACH ROW EXECUTE FUNCTION public.resolve_moderation_cases_for_deleted_target('profile');


--
-- Name: follows set_initial_follow_status; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER set_initial_follow_status BEFORE INSERT ON public.follows FOR EACH ROW EXECUTE FUNCTION public.set_follow_initial_status();


--
-- Name: support_ticket_messages support_ticket_messages_touch; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER support_ticket_messages_touch AFTER INSERT ON public.support_ticket_messages FOR EACH ROW EXECUTE FUNCTION public.touch_ticket_on_message();


--
-- Name: profiles sync_role_trigger; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER sync_role_trigger AFTER UPDATE OF role ON public.profiles FOR EACH ROW WHEN ((old.role IS DISTINCT FROM new.role)) EXECUTE FUNCTION public.sync_role_to_auth();


--
-- Name: forum_thread_reports thread_reports_case; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER thread_reports_case AFTER INSERT ON public.forum_thread_reports FOR EACH ROW EXECUTE FUNCTION public.upsert_moderation_case('forum_thread', 'thread_id');


--
-- Name: comments trg_comments_reply_count; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_comments_reply_count AFTER INSERT OR DELETE ON public.comments FOR EACH ROW EXECUTE FUNCTION public.update_comment_reply_count();


--
-- Name: forum_post_likes trg_forum_post_likes_count; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_forum_post_likes_count AFTER INSERT OR DELETE ON public.forum_post_likes FOR EACH ROW EXECUTE FUNCTION public.fn_forum_post_likes_count();


--
-- Name: forum_thread_replies trg_forum_posts_counts; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_forum_posts_counts AFTER INSERT OR DELETE ON public.forum_thread_replies FOR EACH ROW EXECUTE FUNCTION public.fn_forum_posts_counts();


--
-- Name: forum_thread_likes trg_forum_thread_likes_count; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_forum_thread_likes_count AFTER INSERT OR DELETE ON public.forum_thread_likes FOR EACH ROW EXECUTE FUNCTION public.fn_forum_thread_likes_count();


--
-- Name: forum_thread_topics trg_forum_thread_topics_count; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_forum_thread_topics_count AFTER INSERT OR DELETE ON public.forum_thread_topics FOR EACH ROW EXECUTE FUNCTION public.forum_thread_topics_count();


--
-- Name: forum_threads trg_forum_threads_category_counts; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_forum_threads_category_counts AFTER INSERT OR DELETE ON public.forum_threads FOR EACH ROW EXECUTE FUNCTION public.forum_threads_category_counts();


--
-- Name: forum_threads trg_forum_threads_ranking_score; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_forum_threads_ranking_score BEFORE INSERT OR UPDATE ON public.forum_threads FOR EACH ROW EXECUTE FUNCTION public.fn_forum_threads_ranking();


--
-- Name: forum_threads trg_forum_threads_set_brand; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_forum_threads_set_brand BEFORE INSERT OR UPDATE OF model_id ON public.forum_threads FOR EACH ROW EXECUTE FUNCTION public.fn_forum_threads_set_brand();


--
-- Name: posts trg_posts_ranking_score; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_posts_ranking_score BEFORE INSERT OR UPDATE OF likes_count, comments_count, shares_count, quote_shares_count, saved_count ON public.posts FOR EACH ROW EXECUTE FUNCTION public.compute_post_ranking_score();


--
-- Name: comment_likes trg_sync_comment_likes_count; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_sync_comment_likes_count AFTER INSERT OR DELETE ON public.comment_likes FOR EACH ROW EXECUTE FUNCTION public.sync_comment_likes_count();


--
-- Name: comments trg_sync_post_comments_count; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_sync_post_comments_count AFTER INSERT OR DELETE OR UPDATE OF is_deleted ON public.comments FOR EACH ROW EXECUTE FUNCTION public.sync_post_comments_count();


--
-- Name: post_likes trg_sync_post_likes_count; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_sync_post_likes_count AFTER INSERT OR DELETE ON public.post_likes FOR EACH ROW EXECUTE FUNCTION public.sync_post_likes_count();


--
-- Name: saved_posts trg_sync_post_saved_count; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_sync_post_saved_count AFTER INSERT OR DELETE ON public.saved_posts FOR EACH ROW EXECUTE FUNCTION public.sync_post_saved_count();


--
-- Name: post_shares trg_sync_post_shares_count; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_sync_post_shares_count AFTER INSERT OR DELETE ON public.post_shares FOR EACH ROW EXECUTE FUNCTION public.sync_post_shares_count();


--
-- Name: admin_team_members admin_team_members_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_team_members
    ADD CONSTRAINT admin_team_members_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;


--
-- Name: blocked_accounts blocked_accounts_blocked_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blocked_accounts
    ADD CONSTRAINT blocked_accounts_blocked_id_fkey FOREIGN KEY (blocked_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: blocked_accounts blocked_accounts_blocker_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blocked_accounts
    ADD CONSTRAINT blocked_accounts_blocker_id_fkey FOREIGN KEY (blocker_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: car_gallery car_gallery_car_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_gallery
    ADD CONSTRAINT car_gallery_car_id_fkey FOREIGN KEY (car_id) REFERENCES public.cars(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: car_models car_models_brand_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_models
    ADD CONSTRAINT car_models_brand_id_fkey FOREIGN KEY (brand_id) REFERENCES public.car_brands(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: car_modification_gallery car_modification_gallery_mod_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_modification_gallery
    ADD CONSTRAINT car_modification_gallery_mod_id_fkey FOREIGN KEY (mod_id) REFERENCES public.car_modifications(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: car_modifications car_modifications_car_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_modifications
    ADD CONSTRAINT car_modifications_car_id_fkey FOREIGN KEY (car_id) REFERENCES public.cars(id) ON DELETE CASCADE;


--
-- Name: car_modifications car_modifications_category_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_modifications
    ADD CONSTRAINT car_modifications_category_fkey FOREIGN KEY (category_id) REFERENCES public.car_mod_categories(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: cars cars_brand_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cars
    ADD CONSTRAINT cars_brand_id_fkey FOREIGN KEY (brand_id) REFERENCES public.car_brands(id);


--
-- Name: cars cars_color_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cars
    ADD CONSTRAINT cars_color_id_fkey FOREIGN KEY (color_id) REFERENCES public.car_color_options(id);


--
-- Name: cars cars_drivetrain_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cars
    ADD CONSTRAINT cars_drivetrain_id_fkey FOREIGN KEY (drivetrain_id) REFERENCES public.car_drivetrain_options(id);


--
-- Name: cars cars_fuel_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cars
    ADD CONSTRAINT cars_fuel_id_fkey FOREIGN KEY (fuel_id) REFERENCES public.car_fuel_type_options(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: cars cars_garage_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cars
    ADD CONSTRAINT cars_garage_id_fkey FOREIGN KEY (garage_id) REFERENCES public.garages(id) ON DELETE CASCADE;


--
-- Name: cars cars_mileage_unit_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cars
    ADD CONSTRAINT cars_mileage_unit_id_fkey FOREIGN KEY (mileage_unit_id) REFERENCES public.car_distance_units(id);


--
-- Name: cars cars_model_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cars
    ADD CONSTRAINT cars_model_id_fkey FOREIGN KEY (model_id) REFERENCES public.car_models(id);


--
-- Name: cars cars_status_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cars
    ADD CONSTRAINT cars_status_id_fkey FOREIGN KEY (status_id) REFERENCES public.car_status_options(id) ON UPDATE CASCADE;


--
-- Name: cities cities_country_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cities
    ADD CONSTRAINT cities_country_fkey FOREIGN KEY (country) REFERENCES public.countries(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: comment_likes comment_likes_comment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comment_likes
    ADD CONSTRAINT comment_likes_comment_id_fkey FOREIGN KEY (comment_id) REFERENCES public.comments(id) ON DELETE CASCADE;


--
-- Name: comment_likes comment_likes_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comment_likes
    ADD CONSTRAINT comment_likes_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: comment_reports comment_reports_comment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comment_reports
    ADD CONSTRAINT comment_reports_comment_id_fkey FOREIGN KEY (comment_id) REFERENCES public.comments(id) ON DELETE CASCADE;


--
-- Name: comment_reports comment_reports_reason_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comment_reports
    ADD CONSTRAINT comment_reports_reason_id_fkey FOREIGN KEY (reason_id) REFERENCES public.report_reasons(id);


--
-- Name: comment_reports comment_reports_reporter_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comment_reports
    ADD CONSTRAINT comment_reports_reporter_id_fkey FOREIGN KEY (reporter_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: comments comments_parent_comment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comments
    ADD CONSTRAINT comments_parent_comment_id_fkey FOREIGN KEY (parent_comment_id) REFERENCES public.comments(id) ON DELETE RESTRICT;


--
-- Name: comments comments_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comments
    ADD CONSTRAINT comments_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.posts(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: comments comments_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comments
    ADD CONSTRAINT comments_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: dm_conversations dm_conversations_user_a_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dm_conversations
    ADD CONSTRAINT dm_conversations_user_a_fkey FOREIGN KEY (user_a) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: dm_conversations dm_conversations_user_b_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dm_conversations
    ADD CONSTRAINT dm_conversations_user_b_fkey FOREIGN KEY (user_b) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: dm_message_car_tags dm_message_car_tags_car_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dm_message_car_tags
    ADD CONSTRAINT dm_message_car_tags_car_id_fkey FOREIGN KEY (car_id) REFERENCES public.cars(id) ON DELETE CASCADE;


--
-- Name: dm_message_car_tags dm_message_car_tags_message_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dm_message_car_tags
    ADD CONSTRAINT dm_message_car_tags_message_id_fkey FOREIGN KEY (message_id) REFERENCES public.dm_messages(id) ON DELETE CASCADE;


--
-- Name: dm_messages dm_messages_conversation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dm_messages
    ADD CONSTRAINT dm_messages_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES public.dm_conversations(id) ON DELETE CASCADE;


--
-- Name: dm_messages dm_messages_sender_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dm_messages
    ADD CONSTRAINT dm_messages_sender_id_fkey FOREIGN KEY (sender_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: dm_participant_state dm_participant_state_conversation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dm_participant_state
    ADD CONSTRAINT dm_participant_state_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES public.dm_conversations(id) ON DELETE CASCADE;


--
-- Name: dm_participant_state dm_participant_state_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dm_participant_state
    ADD CONSTRAINT dm_participant_state_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: dream_cars dream_cars_brand_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dream_cars
    ADD CONSTRAINT dream_cars_brand_id_fkey FOREIGN KEY (brand_id) REFERENCES public.car_brands(id);


--
-- Name: dream_cars dream_cars_model_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dream_cars
    ADD CONSTRAINT dream_cars_model_id_fkey FOREIGN KEY (model_id) REFERENCES public.car_models(id);


--
-- Name: dream_cars dream_cars_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dream_cars
    ADD CONSTRAINT dream_cars_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: feedback_comments feedback_comments_feedback_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_comments
    ADD CONSTRAINT feedback_comments_feedback_id_fkey FOREIGN KEY (feedback_id) REFERENCES public.feedback(id) ON DELETE CASCADE;


--
-- Name: feedback_comments feedback_comments_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_comments
    ADD CONSTRAINT feedback_comments_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: feedback feedback_feature_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback
    ADD CONSTRAINT feedback_feature_fkey FOREIGN KEY (feature) REFERENCES public.feedback_feature_options(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: feedback feedback_status_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback
    ADD CONSTRAINT feedback_status_fkey FOREIGN KEY (status) REFERENCES public.feedback_status_options(id) ON DELETE RESTRICT;


--
-- Name: feedback_subscriptions feedback_subscriptions_feedback_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_subscriptions
    ADD CONSTRAINT feedback_subscriptions_feedback_id_fkey FOREIGN KEY (feedback_id) REFERENCES public.feedback(id) ON DELETE CASCADE;


--
-- Name: feedback_subscriptions feedback_subscriptions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_subscriptions
    ADD CONSTRAINT feedback_subscriptions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: feedback feedback_type_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback
    ADD CONSTRAINT feedback_type_fkey FOREIGN KEY (type) REFERENCES public.feedback_type_options(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: feedback_votes feedback_votes_feedback_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_votes
    ADD CONSTRAINT feedback_votes_feedback_id_fkey FOREIGN KEY (feedback_id) REFERENCES public.feedback(id) ON DELETE CASCADE;


--
-- Name: feedback_votes feedback_votes_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_votes
    ADD CONSTRAINT feedback_votes_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: follows follows_follower_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.follows
    ADD CONSTRAINT follows_follower_id_fkey FOREIGN KEY (follower_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: follows follows_following_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.follows
    ADD CONSTRAINT follows_following_id_fkey FOREIGN KEY (following_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: forum_post_likes forum_post_likes_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_post_likes
    ADD CONSTRAINT forum_post_likes_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.forum_thread_replies(id) ON DELETE CASCADE;


--
-- Name: forum_post_likes forum_post_likes_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_post_likes
    ADD CONSTRAINT forum_post_likes_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id);


--
-- Name: forum_thread_replies forum_posts_parent_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_replies
    ADD CONSTRAINT forum_posts_parent_post_id_fkey FOREIGN KEY (parent_post_id) REFERENCES public.forum_thread_replies(id) ON DELETE CASCADE;


--
-- Name: forum_thread_replies forum_posts_thread_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_replies
    ADD CONSTRAINT forum_posts_thread_id_fkey FOREIGN KEY (thread_id) REFERENCES public.forum_threads(id) ON DELETE CASCADE;


--
-- Name: forum_thread_replies forum_posts_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_replies
    ADD CONSTRAINT forum_posts_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id);


--
-- Name: forum_shortcuts forum_shortcuts_brand_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_shortcuts
    ADD CONSTRAINT forum_shortcuts_brand_id_fkey FOREIGN KEY (brand_id) REFERENCES public.car_brands(id);


--
-- Name: forum_shortcuts forum_shortcuts_model_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_shortcuts
    ADD CONSTRAINT forum_shortcuts_model_id_fkey FOREIGN KEY (model_id) REFERENCES public.car_models(id);


--
-- Name: forum_shortcuts forum_shortcuts_topic_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_shortcuts
    ADD CONSTRAINT forum_shortcuts_topic_id_fkey FOREIGN KEY (topic_id) REFERENCES public.forum_topics(id);


--
-- Name: forum_shortcuts forum_shortcuts_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_shortcuts
    ADD CONSTRAINT forum_shortcuts_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id);


--
-- Name: forum_thread_likes forum_thread_likes_thread_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_likes
    ADD CONSTRAINT forum_thread_likes_thread_id_fkey FOREIGN KEY (thread_id) REFERENCES public.forum_threads(id) ON DELETE CASCADE;


--
-- Name: forum_thread_likes forum_thread_likes_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_likes
    ADD CONSTRAINT forum_thread_likes_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id);


--
-- Name: forum_thread_reads forum_thread_reads_thread_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_reads
    ADD CONSTRAINT forum_thread_reads_thread_id_fkey FOREIGN KEY (thread_id) REFERENCES public.forum_threads(id) ON DELETE CASCADE;


--
-- Name: forum_thread_reads forum_thread_reads_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_reads
    ADD CONSTRAINT forum_thread_reads_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: forum_thread_reply_reports forum_thread_reply_reports_reason_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_reply_reports
    ADD CONSTRAINT forum_thread_reply_reports_reason_id_fkey FOREIGN KEY (reason_id) REFERENCES public.report_reasons(id);


--
-- Name: forum_thread_reply_reports forum_thread_reply_reports_reply_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_reply_reports
    ADD CONSTRAINT forum_thread_reply_reports_reply_id_fkey FOREIGN KEY (reply_id) REFERENCES public.forum_thread_replies(id) ON DELETE CASCADE;


--
-- Name: forum_thread_reply_reports forum_thread_reply_reports_reporter_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_reply_reports
    ADD CONSTRAINT forum_thread_reply_reports_reporter_id_fkey FOREIGN KEY (reporter_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: forum_thread_reports forum_thread_reports_reason_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_reports
    ADD CONSTRAINT forum_thread_reports_reason_id_fkey FOREIGN KEY (reason_id) REFERENCES public.report_reasons(id);


--
-- Name: forum_thread_reports forum_thread_reports_reporter_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_reports
    ADD CONSTRAINT forum_thread_reports_reporter_id_fkey FOREIGN KEY (reporter_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: forum_thread_reports forum_thread_reports_thread_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_reports
    ADD CONSTRAINT forum_thread_reports_thread_id_fkey FOREIGN KEY (thread_id) REFERENCES public.forum_threads(id) ON DELETE CASCADE;


--
-- Name: forum_thread_saves forum_thread_saves_thread_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_saves
    ADD CONSTRAINT forum_thread_saves_thread_id_fkey FOREIGN KEY (thread_id) REFERENCES public.forum_threads(id) ON DELETE CASCADE;


--
-- Name: forum_thread_saves forum_thread_saves_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_saves
    ADD CONSTRAINT forum_thread_saves_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: forum_thread_topics forum_thread_topics_thread_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_topics
    ADD CONSTRAINT forum_thread_topics_thread_id_fkey FOREIGN KEY (thread_id) REFERENCES public.forum_threads(id) ON DELETE CASCADE;


--
-- Name: forum_thread_topics forum_thread_topics_topic_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_topics
    ADD CONSTRAINT forum_thread_topics_topic_id_fkey FOREIGN KEY (topic_id) REFERENCES public.forum_topics(id) ON DELETE RESTRICT;


--
-- Name: forum_threads forum_threads_brand_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_threads
    ADD CONSTRAINT forum_threads_brand_id_fkey FOREIGN KEY (brand_id) REFERENCES public.car_brands(id);


--
-- Name: forum_threads forum_threads_model_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_threads
    ADD CONSTRAINT forum_threads_model_id_fkey FOREIGN KEY (model_id) REFERENCES public.car_models(id);


--
-- Name: forum_threads forum_threads_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_threads
    ADD CONSTRAINT forum_threads_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id);


--
-- Name: garages garages_owner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.garages
    ADD CONSTRAINT garages_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: moderation_actions moderation_actions_case_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.moderation_actions
    ADD CONSTRAINT moderation_actions_case_id_fkey FOREIGN KEY (case_id) REFERENCES public.moderation_cases(id) ON DELETE SET NULL;


--
-- Name: moderation_actions moderation_actions_target_author_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.moderation_actions
    ADD CONSTRAINT moderation_actions_target_author_id_fkey FOREIGN KEY (target_author_id) REFERENCES public.profiles(id) ON DELETE SET NULL;


--
-- Name: notification_preferences notification_preferences_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_preferences
    ADD CONSTRAINT notification_preferences_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: notifications notifications_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: post_images post_images_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_images
    ADD CONSTRAINT post_images_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.posts(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: post_likes post_likes_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_likes
    ADD CONSTRAINT post_likes_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.posts(id) ON DELETE CASCADE;


--
-- Name: post_likes post_likes_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_likes
    ADD CONSTRAINT post_likes_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: post_reports post_reports_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_reports
    ADD CONSTRAINT post_reports_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.posts(id) ON DELETE CASCADE;


--
-- Name: post_reports post_reports_reason_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_reports
    ADD CONSTRAINT post_reports_reason_id_fkey FOREIGN KEY (reason_id) REFERENCES public.report_reasons(id);


--
-- Name: post_reports post_reports_reporter_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_reports
    ADD CONSTRAINT post_reports_reporter_id_fkey FOREIGN KEY (reporter_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: post_shares post_shares_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_shares
    ADD CONSTRAINT post_shares_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.posts(id) ON DELETE CASCADE;


--
-- Name: post_shares post_shares_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_shares
    ADD CONSTRAINT post_shares_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: posts posts_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.posts
    ADD CONSTRAINT posts_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: profile_car_categories_junction profile_car_categories_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profile_car_categories_junction
    ADD CONSTRAINT profile_car_categories_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.car_category_options(id);


--
-- Name: profile_car_categories_junction profile_car_categories_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profile_car_categories_junction
    ADD CONSTRAINT profile_car_categories_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: profile_community_roles_junction profile_community_roles_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profile_community_roles_junction
    ADD CONSTRAINT profile_community_roles_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: profile_community_roles_junction profile_community_roles_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profile_community_roles_junction
    ADD CONSTRAINT profile_community_roles_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.community_role_options(id);


--
-- Name: profile_reports profile_reports_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profile_reports
    ADD CONSTRAINT profile_reports_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: profile_reports profile_reports_reason_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profile_reports
    ADD CONSTRAINT profile_reports_reason_id_fkey FOREIGN KEY (reason_id) REFERENCES public.report_reasons(id);


--
-- Name: profile_reports profile_reports_reporter_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profile_reports
    ADD CONSTRAINT profile_reports_reporter_id_fkey FOREIGN KEY (reporter_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: profiles profiles_app_language_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profiles
    ADD CONSTRAINT profiles_app_language_fkey FOREIGN KEY (app_language) REFERENCES public.app_language_options(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: profiles profiles_city_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profiles
    ADD CONSTRAINT profiles_city_id_fkey FOREIGN KEY (city_id) REFERENCES public.cities(id);


--
-- Name: profiles profiles_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.profiles
    ADD CONSTRAINT profiles_id_fkey FOREIGN KEY (id) REFERENCES auth.users(id) ON DELETE CASCADE;


--
-- Name: saved_posts saved_posts_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.saved_posts
    ADD CONSTRAINT saved_posts_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.posts(id) ON DELETE CASCADE;


--
-- Name: saved_posts saved_posts_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.saved_posts
    ADD CONSTRAINT saved_posts_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: support_ticket_messages support_ticket_messages_ticket_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_ticket_messages
    ADD CONSTRAINT support_ticket_messages_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES public.support_tickets(id) ON DELETE CASCADE;


--
-- Name: support_tickets support_tickets_category_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_tickets
    ADD CONSTRAINT support_tickets_category_fkey FOREIGN KEY (category) REFERENCES public.support_ticket_categories(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: support_tickets support_tickets_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_tickets
    ADD CONSTRAINT support_tickets_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: tagged_cars tagged_cars_car_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tagged_cars
    ADD CONSTRAINT tagged_cars_car_id_fkey FOREIGN KEY (car_id) REFERENCES public.cars(id) ON DELETE CASCADE;


--
-- Name: tagged_cars tagged_cars_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tagged_cars
    ADD CONSTRAINT tagged_cars_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.posts(id) ON DELETE CASCADE;


--
-- Name: tagged_people tagged_people_post_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tagged_people
    ADD CONSTRAINT tagged_people_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.posts(id) ON DELETE CASCADE;


--
-- Name: tagged_people tagged_people_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tagged_people
    ADD CONSTRAINT tagged_people_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: user_presence user_presence_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_presence
    ADD CONSTRAINT user_presence_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: car_category_options Allow authenticated read access; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: cities Allow authenticated read access; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: community_role_options Allow authenticated read access; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: countries Allow authenticated read access; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: profiles Any logged in user can view someone else's profile; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: report_reasons Authenticated users can read report reasons; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: profiles Users can edit only their own profile; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: blocked_accounts Users can view their own blocked accounts; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: comment_reports Users can view their own comment reports; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: forum_thread_reply_reports Users can view their own forum thread reply reports; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: forum_thread_reports Users can view their own forum thread reports; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: post_reports Users can view their own post reports; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: profile_reports Users can view their own profile reports; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: admin_team_members; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: app_language_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: blocked_accounts; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_brands; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_brands car_brands_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: car_category_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_color_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_color_options car_color_options_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: car_distance_units; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_distance_units car_distance_units_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: car_drivetrain_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_drivetrain_options car_drivetrain_options_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: car_fuel_type_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_fuel_type_options car_fuel_type_options_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: car_gallery; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_gallery car_gallery_delete_own_car; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: car_gallery car_gallery_insert_own_car; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: car_gallery car_gallery_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: car_gallery car_gallery_update_own_car; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: car_mod_categories; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_mod_categories car_mod_categories_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: car_models; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_models car_models_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: car_modification_gallery; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_modifications; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_status_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_status_options car_status_options_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: cars; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: cars cars_delete_own_garage; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: cars cars_insert_own_garage; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: cars cars_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: cars cars_update_own_garage; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: cities; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: comment_likes; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: comment_likes comment_likes_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: comment_reports; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: comments; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: comments comments_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: community_role_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: countries; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: dm_conversations; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: dm_message_car_tags; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: dm_messages; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: dm_participant_state; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: dream_cars; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: dream_cars dream_cars_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: feedback; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: feedback_comments; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: feedback_feature_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: feedback_feature_options feedback_feature_options_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: feedback feedback_select_own; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: feedback_status_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: feedback_status_options feedback_status_options_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: feedback_subscriptions; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: feedback_type_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: feedback_type_options feedback_type_options_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: feedback_votes; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: follows; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: follows follows_delete_own_or_received; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: follows follows_insert_own; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: follows follows_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: follows follows_update_received; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: forum_post_likes; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: forum_post_likes forum_post_likes_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: forum_thread_replies forum_posts_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: forum_shortcuts; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: forum_shortcuts forum_shortcuts_select_own; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: forum_thread_likes; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: forum_thread_likes forum_thread_likes_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: forum_thread_reads; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: forum_thread_replies; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: forum_thread_reply_reports; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: forum_thread_reports; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: forum_thread_saves; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: forum_thread_topics; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: forum_thread_topics forum_thread_topics_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: forum_threads; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: forum_threads forum_threads_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: forum_topics; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: forum_topics forum_topics_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: garages; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: garages garages_delete_own; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: garages garages_insert_own; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: garages garages_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: garages garages_update_own; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: car_modification_gallery mod_gallery_delete_own_car; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: car_modification_gallery mod_gallery_insert_own_car; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: car_modification_gallery mod_gallery_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: car_modification_gallery mod_gallery_update_own_car; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: moderation_actions; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: moderation_cases; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_modifications mods_delete_own_car; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: car_modifications mods_insert_own_car; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: car_modifications mods_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: car_modifications mods_update_own_car; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: notification_preferences; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: notification_preferences notification_preferences_select_own; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: notifications; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: post_images; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: post_images post_images_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: post_likes; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: post_likes post_likes_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: post_reports; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: post_shares; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: post_shares post_shares_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: posts; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: posts posts_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: price_currencies_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: price_currencies_options price_currencies_options_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: profile_car_categories_junction; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: profile_car_categories_junction profile_car_categories_junction_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: profile_community_roles_junction; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: profile_community_roles_junction profile_community_roles_junction_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: profile_reports; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: profiles; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: report_reasons; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: saved_posts; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: saved_posts saved_posts_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: support_ticket_categories; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: support_ticket_messages; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: support_tickets; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: tagged_cars; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: tagged_cars tagged_cars_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: tagged_people; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: tagged_people tagged_people_select_authenticated; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: user_presence; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- PostgreSQL database dump complete
--


