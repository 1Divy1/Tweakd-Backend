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
-- Name: dm_push_bundle(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.dm_push_bundle(p_notification_id uuid) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
declare
  v_n      public.notifications%rowtype;
  v_unread bigint;
  v_result jsonb;
begin
  select * into v_n from public.notifications where id = p_notification_id;

  -- Lock #1: this RPC will not serve anything that is not a DM, so a leaked
  -- webhook secret cannot be used to re-fire or spoof any other push type.
  if not found or v_n.type <> 'dm' then
    return null;
  end if;

  if exists (
    select 1 from public.notification_preferences p
     where p.profile_id = v_n.user_id and p.dms_enabled = false
  ) then
    return null;
  end if;

  if v_n.payload ? 'actor_id' and exists (
    select 1 from public.blocked_accounts b
     where b.blocker_id = v_n.user_id
       and b.blocked_id = (v_n.payload ->> 'actor_id')::uuid
  ) then
    return null;
  end if;

  select count(*) into v_unread
    from public.notifications n
   where n.user_id = v_n.user_id and n.is_read = false;

  select jsonb_build_object(
           'notification_id', v_n.id,
           'title',           v_n.title,
           'body',            v_n.body,
           'payload',         coalesce(v_n.payload, '{}'::jsonb),
           'unread_count',    v_unread,
           'tokens',          coalesce(jsonb_agg(d.token), '[]'::jsonb))
    into v_result
    from public.user_devices_firebase_token d
   where d.user_id = v_n.user_id;

  return v_result;
end;
$$;


--
-- Name: dm_push_notify(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.dm_push_notify() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
declare
  v_secret text;
begin
  select decrypted_secret into v_secret
    from vault.decrypted_secrets
   where name = 'dm_push_webhook_secret';

  -- A missing secret must never break a DM send; the message still delivers,
  -- only the push is skipped.
  if v_secret is null then
    return null;
  end if;

  -- URL and anon key are public by design (the anon key ships in the Flutter
  -- app); the anon bearer only satisfies the gateway's verify_jwt. The actual
  -- authentication is x-webhook-secret, compared in constant time by the
  -- function. net.http_post queues asynchronously, so DM send latency is
  -- unchanged, and a rolled-back transaction cancels the queued request.
  perform net.http_post(
    url     := 'https://fybgmaigzidhbmhbgfhu.supabase.co/functions/v1/dm-push',
    headers := jsonb_build_object(
                 'Content-Type',     'application/json',
                 'Authorization',    'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZ5YmdtYWlnemlkaGJtaGJnZmh1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzYzNDU1MjAsImV4cCI6MjA5MTkyMTUyMH0.luKclQDTi7D-kQCLoKd8yxqopqWr-Uiiar95qnz4Zqs',
                 'x-webhook-secret', v_secret),
    body    := jsonb_build_object('notification_id', new.id),
    timeout_milliseconds := 5000
  );
  return null;
end;
$$;


--
-- Name: dm_send_message(uuid, text, uuid[]); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.dm_send_message(p_recipient_id uuid, p_content text DEFAULT ''::text, p_tagged_car_ids uuid[] DEFAULT '{}'::uuid[]) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
declare
  v_sender          uuid        := auth.uid();
  v_content         text        := coalesce(p_content, '');
  v_cars            uuid[]      := coalesce(p_tagged_car_ids, '{}'::uuid[]);
  v_user_a          uuid;
  v_user_b          uuid;
  v_conversation_id uuid;
  v_message_id      uuid        := gen_random_uuid();
  v_now             timestamptz := now();
begin
  if v_sender is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;

  if p_recipient_id is null then
    raise exception 'recipient_id is required' using errcode = '22023';
  end if;

  if p_recipient_id = v_sender then
    raise exception 'cannot send a message to yourself' using errcode = '22023';
  end if;

  if not exists (select 1 from public.profiles p where p.id = p_recipient_id) then
    raise exception 'recipient not found' using errcode = '22023';
  end if;

  -- A send needs text, cars, or both — never an empty payload.
  if btrim(v_content) = '' and coalesce(cardinality(v_cars), 0) = 0 then
    raise exception 'message must have content or at least one tagged car'
      using errcode = '22023';
  end if;

  if length(v_content) > 2000 then
    raise exception 'content exceeds 2000 characters' using errcode = '22001';
  end if;

  -- Canonical pair ordering — dm_conversations has CHECK (user_a < user_b)
  -- and UNIQUE (user_a, user_b), so one pair is always one row.
  v_user_a := least(v_sender, p_recipient_id);
  v_user_b := greatest(v_sender, p_recipient_id);

  insert into public.dm_conversations (id, user_a, user_b, created_at)
  values (gen_random_uuid(), v_user_a, v_user_b, v_now)
  on conflict (user_a, user_b) do nothing;

  select c.id
    into v_conversation_id
    from public.dm_conversations c
   where c.user_a = v_user_a
     and c.user_b = v_user_b;

  insert into public.dm_messages
    (id, conversation_id, sender_id, content, is_deleted, created_at)
  values
    (v_message_id, v_conversation_id, v_sender, v_content, false, v_now);

  -- Unknown car ids are skipped rather than raising, so one stale id from a
  -- car deleted mid-compose cannot fail the whole send.
  if coalesce(cardinality(v_cars), 0) > 0 then
    insert into public.dm_message_car_tags (message_id, car_id, created_at)
    select v_message_id, c.id, v_now
      from public.cars c
     where c.id = any (v_cars)
    on conflict do nothing;
  end if;

  -- A blank preview (car-only share) is meaningful to the app: the inbox row
  -- renders "shared cars". NULL is reserved for "last message was deleted".
  update public.dm_conversations
     set last_message_at        = v_now,
         last_message_preview   = v_content,
         last_message_sender_id = v_sender
   where id = v_conversation_id;

  -- Sender side: make sure the row exists and the chat is no longer hidden.
  -- unread_count is left alone; the chat page marks the conversation read.
  insert into public.dm_participant_state (conversation_id, user_id, unread_count)
  values (v_conversation_id, v_sender, 0)
  on conflict (conversation_id, user_id) do update
    set hidden_at = null;

  -- Recipient side: bump the badge and resurface a chat they had hidden.
  insert into public.dm_participant_state (conversation_id, user_id, unread_count)
  values (v_conversation_id, p_recipient_id, 1)
  on conflict (conversation_id, user_id) do update
    set unread_count = public.dm_participant_state.unread_count + 1,
        hidden_at    = null;

  -- Feed row for the recipient. Spring's push pipeline never touches 'dm';
  -- the notifications_dm_push trigger picks this row up instead. Same
  -- transaction as the send, so a failed send leaves no orphan notification.
  insert into public.notifications (id, user_id, type, title, body, payload, is_read, created_at)
  select
    gen_random_uuid(),
    p_recipient_id,
    'dm',
    coalesce(nullif(s.username, ''), 'Someone') || ' sent you a message',
    case when btrim(v_content) = '' then 'Shared a car' else left(v_content, 200) end,
    jsonb_build_object(
      'actor_id',        v_sender::text,
      'actor_username',  coalesce(nullif(s.username, ''), 'Someone'),
      'conversation_id', v_conversation_id::text,
      'message_id',      v_message_id::text
    ),
    false,
    v_now
  from public.profiles s
  where s.id = v_sender;

  return jsonb_build_object(
    'id',              v_message_id,
    'conversation_id', v_conversation_id,
    'sender_id',       v_sender,
    'content',         v_content,
    'deleted',         false,
    'created_at',      to_jsonb(v_now)
  );
end;
$$;


--
-- Name: dm_topic_is_peer(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.dm_topic_is_peer(topic text) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO ''
    AS $$
  select exists (
    select 1
    from public.dm_conversations c
    where (c.user_a = auth.uid()
           and c.user_b::text = substring(topic from 6))
       or (c.user_b = auth.uid()
           and c.user_a::text = substring(topic from 6))
  );
$$;


--
-- Name: fn_forum_post_likes_count(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_forum_post_likes_count() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
begin
    if tg_op = 'INSERT' then
        update public.forum_thread_replies set likes_count = likes_count + 1 where id = new.post_id;
        return new;
    elsif tg_op = 'DELETE' then
        update public.forum_thread_replies set likes_count = greatest(likes_count - 1, 0) where id = old.post_id;
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
            update public.forum_thread_replies set reply_count = reply_count + 1 where id = new.parent_post_id;
        end if;
        return new;
    elsif tg_op = 'DELETE' then
        update public.forum_threads
            set reply_count = greatest(reply_count - 1, 0)
            where id = old.thread_id;
        if old.parent_post_id is not null then
            update public.forum_thread_replies set reply_count = greatest(reply_count - 1, 0) where id = old.parent_post_id;
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
    update public.forum_thread_topic_options set thread_count = thread_count + 1 where id = new.topic_id;
  elsif tg_op = 'DELETE' then
    update public.forum_thread_topic_options set thread_count = greatest(thread_count - 1, 0) where id = old.topic_id;
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
-- Name: set_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END;
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
-- Name: trg_feedback_feed_block_completed_votes(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trg_feedback_feed_block_completed_votes() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
declare
    msg_status text;
    target_message_id uuid;
begin
    target_message_id := coalesce(NEW.message_id, OLD.message_id);

    select status into msg_status
    from feedback_feed_messages
    where id = target_message_id;

    if msg_status = 'completed' then
        if TG_OP = 'DELETE' then
            raise exception 'Cannot remove a vote from a completed feedback message';
        else
            raise exception 'Cannot vote on a completed feedback message';
        end if;
    end if;

    if TG_OP = 'DELETE' then
        return OLD;
    else
        return NEW;
    end if;
end;
$$;


--
-- Name: trg_feedback_feed_notify_author_status_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trg_feedback_feed_notify_author_status_change() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
declare
    new_status_label text;
begin
    if NEW.status is distinct from OLD.status then
        select status into new_status_label
        from feedback_feed_status_options
        where id = NEW.status;

        insert into notifications (user_id, type, title, body, payload)
        values (
            NEW.author_id,
            'feedback_status_changed',
            'Your feedback status has been updated !',
            'Your feedback status is now: ' || coalesce(new_status_label, NEW.status),
            jsonb_build_object(
                'message_id', NEW.id,
                'old_status', OLD.status,
                'new_status', NEW.status
            )
        );
    end if;

    return NEW;
end;
$$;


--
-- Name: trg_feedback_feed_stamp_completed_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trg_feedback_feed_stamp_completed_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
begin
    if TG_OP = 'INSERT' then
        if NEW.status = 'completed' and NEW.completed_at is null then
            NEW.completed_at := now();
        elsif NEW.status <> 'completed' then
            NEW.completed_at := null;
        end if;
        return NEW;
    end if;

    if NEW.status is distinct from OLD.status then
        if NEW.status = 'completed' then
            NEW.completed_at := now();
        else
            NEW.completed_at := null;
        end if;
    end if;
    return NEW;
end;
$$;


--
-- Name: trg_feedback_feed_update_vote_counts(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trg_feedback_feed_update_vote_counts() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
begin
    if TG_OP = 'INSERT' then
        if NEW.vote_type = 1 then
            update feedback_feed_messages
            set up_votes = up_votes + 1,
                net_votes = net_votes + 1
            where id = NEW.message_id;
        else
            update feedback_feed_messages
            set down_votes = down_votes + 1,
                net_votes = net_votes - 1
            where id = NEW.message_id;
        end if;
        return NEW;

    elsif TG_OP = 'UPDATE' then
        if OLD.vote_type <> NEW.vote_type then
            if NEW.vote_type = 1 then
                update feedback_feed_messages
                set up_votes = up_votes + 1,
                    down_votes = down_votes - 1,
                    net_votes = net_votes + 2
                where id = NEW.message_id;
            else
                update feedback_feed_messages
                set down_votes = down_votes + 1,
                    up_votes = up_votes - 1,
                    net_votes = net_votes - 2
                where id = NEW.message_id;
            end if;
        end if;
        return NEW;

    elsif TG_OP = 'DELETE' then
        if OLD.vote_type = 1 then
            update feedback_feed_messages
            set up_votes = up_votes - 1,
                net_votes = net_votes - 1
            where id = OLD.message_id;
        else
            update feedback_feed_messages
            set down_votes = down_votes - 1,
                net_votes = net_votes + 1
            where id = OLD.message_id;
        end if;
        return OLD;
    end if;

    return null;
end;
$$;


--
-- Name: update_car_event_attendees_count(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_car_event_attendees_count() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  -- INSERT: new row added (either 'interested' or 'attending')
  IF TG_OP = 'INSERT' THEN
    IF NEW.status = 'attending' THEN
      UPDATE car_events
      SET attendees_count = attendees_count + 1
      WHERE id = NEW.event_id;
    END IF;
    RETURN NEW;
  END IF;

  -- DELETE: user removed their RSVP (hard delete)
  IF TG_OP = 'DELETE' THEN
    IF OLD.status = 'attending' THEN
      UPDATE car_events
      SET attendees_count = attendees_count - 1
      WHERE id = OLD.event_id;
    END IF;
    RETURN OLD;
  END IF;

  -- UPDATE: only realistic transition is interested <-> attending
  IF TG_OP = 'UPDATE' THEN
    IF OLD.status = NEW.status THEN
      RETURN NEW; -- nothing relevant changed
    END IF;

    IF OLD.status = 'attending' AND NEW.status = 'interested' THEN
      UPDATE car_events SET attendees_count = attendees_count - 1 WHERE id = NEW.event_id;
    ELSIF OLD.status = 'interested' AND NEW.status = 'attending' THEN
      UPDATE car_events SET attendees_count = attendees_count + 1 WHERE id = NEW.event_id;
    END IF;

    RETURN NEW;
  END IF;

  RETURN NULL;
END;
$$;


--
-- Name: update_car_event_attending_cars_count(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_car_event_attending_cars_count() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  -- 'withdrawn' means a withdrawal request is pending organizer review: the participant is
  -- still counted as attending until the organizer approves the withdrawal (which hard-deletes
  -- the row) or rejects it (which reverts the row to 'accepted'). So both 'accepted' and
  -- 'withdrawn' are "counted" states; only a transition into/out of that set changes the count.

  -- INSERT: new row added directly as 'accepted' (e.g. open events, no approval needed)
  IF TG_OP = 'INSERT' THEN
    IF NEW.status IN ('accepted', 'withdrawn') THEN
      UPDATE car_events
      SET attending_cars_count = attending_cars_count + 1
      WHERE id = NEW.event_id;
    END IF;
    RETURN NEW;
  END IF;

  -- DELETE: participation removed (organizer approving a withdrawal deletes 'withdrawn' rows)
  IF TG_OP = 'DELETE' THEN
    IF OLD.status IN ('accepted', 'withdrawn') THEN
      UPDATE car_events
      SET attending_cars_count = attending_cars_count - 1
      WHERE id = OLD.event_id;
    END IF;
    RETURN OLD;
  END IF;

  -- UPDATE: status transition (pending/rejected <-> accepted <-> withdrawn)
  IF TG_OP = 'UPDATE' THEN
    IF (OLD.status IN ('accepted', 'withdrawn')) = (NEW.status IN ('accepted', 'withdrawn')) THEN
      RETURN NEW;
    END IF;

    IF OLD.status IN ('accepted', 'withdrawn') AND NEW.status NOT IN ('accepted', 'withdrawn') THEN
      UPDATE car_events SET attending_cars_count = attending_cars_count - 1 WHERE id = NEW.event_id;
    ELSIF OLD.status NOT IN ('accepted', 'withdrawn') AND NEW.status IN ('accepted', 'withdrawn') THEN
      UPDATE car_events SET attending_cars_count = attending_cars_count + 1 WHERE id = NEW.event_id;
    END IF;

    RETURN NEW;
  END IF;

  RETURN NULL;
END;
$$;


--
-- Name: trg_contest_vote_counts(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trg_contest_vote_counts() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
begin
  if tg_op = 'INSERT' then
    update public.car_event_contest_entries
       set votes_count = votes_count + 1, last_vote_at = now()
     where contest_id = new.contest_id and car_id = new.car_id;
    update public.car_event_contests
       set votes_count = votes_count + 1
     where id = new.contest_id;
    return new;
  elsif tg_op = 'UPDATE' then
    if new.car_id <> old.car_id then
      update public.car_event_contest_entries
         set votes_count = votes_count - 1
       where contest_id = old.contest_id and car_id = old.car_id;
      update public.car_event_contest_entries
         set votes_count = votes_count + 1, last_vote_at = now()
       where contest_id = new.contest_id and car_id = new.car_id;
    end if;
    return new;
  elsif tg_op = 'DELETE' then
    update public.car_event_contest_entries
       set votes_count = votes_count - 1
     where contest_id = old.contest_id and car_id = old.car_id;
    update public.car_event_contests
       set votes_count = votes_count - 1
     where id = old.contest_id;
    return old;
  end if;
  return null;
end;
$$;


--
-- Name: trg_contest_entries_count(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.trg_contest_entries_count() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
begin
  if tg_op = 'INSERT' then
    if new.status = 'accepted' then
      update public.car_event_contests set entries_count = entries_count + 1 where id = new.contest_id;
    end if;
    return new;
  elsif tg_op = 'UPDATE' then
    if (old.status = 'accepted') <> (new.status = 'accepted') then
      update public.car_event_contests
         set entries_count = entries_count + (case when new.status = 'accepted' then 1 else -1 end)
       where id = new.contest_id;
    end if;
    return new;
  elsif tg_op = 'DELETE' then
    if old.status = 'accepted' then
      update public.car_event_contests set entries_count = entries_count - 1 where id = old.contest_id;
    end if;
    return old;
  end if;
  return null;
end;
$$;


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


--
-- Name: user_devices_firebase_token_touch(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.user_devices_firebase_token_touch() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO ''
    AS $$
begin
    new.updated_at := now();
    return new;
end;
$$;


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
-- Name: badges; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.badges (
    id text NOT NULL,
    title text NOT NULL,
    description text,
    unlocked_badge_url text NOT NULL,
    is_available boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    locked_badge_url text,
    award_trigger text,
    earnable_from timestamp with time zone,
    earnable_until timestamp with time zone,
    CONSTRAINT badges_award_trigger_known_ck CHECK (((award_trigger IS NULL) OR (award_trigger = ANY (ARRAY['account_created'::text, 'contest_first_place'::text, 'contest_second_place'::text, 'contest_third_place'::text])))),
    CONSTRAINT badges_earnable_window_ck CHECK (((earnable_from IS NULL) OR (earnable_until IS NULL) OR (earnable_from < earnable_until))),
    CONSTRAINT badges_urls_are_keys CHECK (((unlocked_badge_url !~~ 'http%'::text) AND ((locked_badge_url IS NULL) OR (locked_badge_url !~~ 'http%'::text))))
);


--
-- Name: TABLE badges; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.badges IS 'Admin-managed catalogue of unlockable badges. Retire a badge with is_available = false — never DELETE, or you orphan the user_badges rows that earned it (the FK is ON DELETE RESTRICT and will refuse anyway).';


--
-- Name: COLUMN badges.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.badges.id IS 'Stable code, referenced by user_badges.badge_id and by the Badges constants in the backend. Renaming it cascades to holders.';


--
-- Name: COLUMN badges.unlocked_badge_url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.badges.unlocked_badge_url IS 'R2 object key of the earned artwork within the app-assets bucket, e.g. ''badges/pioneer/badge-unlocked.svg''. A key, not a URL — the backend builds the full URL.';


--
-- Name: COLUMN badges.is_available; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.badges.is_available IS 'false = retired: cannot be awarded and is hidden from the catalogue, but stays readable on the profiles that already hold it. The manual half of awardability — earnable_from/earnable_until are the automatic half, and a badge must satisfy both.';


--
-- Name: COLUMN badges.locked_badge_url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.badges.locked_badge_url IS 'R2 object key of the not-yet-earned artwork. NULL = no locked variant; the client greys the unlocked one out itself.';


--
-- Name: COLUMN badges.award_trigger; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.badges.award_trigger IS 'The event that unlocks this badge automatically, matching the BadgeTrigger enum in the backend. NULL = hand-granted only. Known codes: account_created. Adding one means extending badges_award_trigger_known_ck and BadgeTrigger in the same release.';


--
-- Name: COLUMN badges.earnable_from; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.badges.earnable_from IS 'Start of the window in which this badge may be awarded automatically, inclusive. NULL = no start bound. Lets a badge be staged before it opens.';


--
-- Name: COLUMN badges.earnable_until; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.badges.earnable_until IS 'End of the window in which this badge may be awarded automatically, exclusive. NULL = no end bound. This is how a limited-time badge retires itself; the users already holding it are untouched.';


--
-- Name: blocked_accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.blocked_accounts (
    blocker_id uuid NOT NULL,
    blocked_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: business_account_active_status_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.business_account_active_status_options (
    id text NOT NULL,
    status text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE business_account_active_status_options; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.business_account_active_status_options IS 'Reference table for admins to manage the status of a business account';


--
-- Name: business_account_verification_status_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.business_account_verification_status_options (
    id text NOT NULL,
    status text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE business_account_verification_status_options; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.business_account_verification_status_options IS 'Reference table for the account''s verification status';


--
-- Name: business_accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.business_accounts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name text NOT NULL,
    type text NOT NULL,
    phone_number text,
    email text,
    website_url text,
    address text NOT NULL,
    location public.geography(Point,4326) NOT NULL,
    city text NOT NULL,
    verification_status text NOT NULL,
    active_status text NOT NULL,
    verified_at timestamp with time zone,
    logo_url text,
    description text,
    average_rating numeric DEFAULT 0 NOT NULL,
    review_count integer DEFAULT 0 NOT NULL,
    follower_count integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    timezone text DEFAULT 'Europe/Bucharest'::text NOT NULL,
    rejection_reason text,
    reviewed_by uuid,
    reviewed_at timestamp with time zone,
    CONSTRAINT business_accounts_timezone_valid CHECK (((now() AT TIME ZONE timezone) IS NOT NULL))
);


--
-- Name: COLUMN business_accounts.timezone; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.business_accounts.timezone IS 'IANA zone id (e.g. Europe/Bucharest) used to interpret this business''s opening hours.';


--
-- Name: business_hours; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.business_hours (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    business_id uuid NOT NULL,
    weekday smallint NOT NULL,
    opening_hour time without time zone,
    closing_hour time without time zone,
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    is_closed boolean DEFAULT false NOT NULL,
    CONSTRAINT business_hours_times_present CHECK (((is_closed AND (opening_hour IS NULL) AND (closing_hour IS NULL)) OR ((NOT is_closed) AND (opening_hour IS NOT NULL) AND (closing_hour IS NOT NULL)))),
    CONSTRAINT business_hours_weekday_range CHECK (((weekday >= 1) AND (weekday <= 7)))
);


--
-- Name: TABLE business_hours; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.business_hours IS 'The weekly working hours for that business (eg: monday to friday from 8 a.m. to 17 p.m.)';


--
-- Name: COLUMN business_hours.weekday; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.business_hours.weekday IS 'ISO-8601 day of week: 1 = Monday … 7 = Sunday (java.time.DayOfWeek.getValue()).';


--
-- Name: COLUMN business_hours.closing_hour; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.business_hours.closing_hour IS 'Interpreted in business_accounts.timezone. A closing_hour <= opening_hour means the shift runs past midnight into the next day.';


--
-- Name: business_type_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.business_type_options (
    id text NOT NULL,
    type text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE business_type_options; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.business_type_options IS 'A predefined list of business categories for the auto industry';


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
-- Name: car_event_approval_status_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_event_approval_status_options (
    id text NOT NULL,
    status text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE car_event_approval_status_options; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.car_event_approval_status_options IS 'An event''s status, updated by the admin team.';


--
-- Name: car_event_attendee_status; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_event_attendee_status (
    id text NOT NULL,
    status text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE car_event_attendee_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.car_event_attendee_status IS 'Reference table';


--
-- Name: car_event_attendees_list; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_event_attendees_list (
    user_id uuid NOT NULL,
    event_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    status text NOT NULL
);


--
-- Name: TABLE car_event_attendees_list; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.car_event_attendees_list IS 'The list of spectators for a specific event.';


--
-- Name: car_event_categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_event_categories (
    id text NOT NULL,
    category text NOT NULL,
    is_available boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE car_event_categories; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.car_event_categories IS 'What type of events can occur on the virtual map of the app (eg: car meets, convoys, track days, etc.)';


--
-- Name: car_event_contest_categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_event_contest_categories (
    id text NOT NULL,
    label text NOT NULL,
    icon text NOT NULL,
    sort_order smallint NOT NULL,
    is_available boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE car_event_contest_categories; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.car_event_contest_categories IS 'The categories an organizer can pick when creating a contest inside an event. `custom` lets them type their own title. Retire one with is_available = false; never delete (contests reference it).';


--
-- Name: car_event_contest_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_event_contest_entries (
    contest_id uuid NOT NULL,
    car_id uuid NOT NULL,
    owner_id uuid NOT NULL,
    status text DEFAULT 'pending'::text NOT NULL,
    rejection_reason text,
    requested_at timestamp with time zone DEFAULT now() NOT NULL,
    decided_at timestamp with time zone,
    decided_by uuid,
    votes_count integer DEFAULT 0 NOT NULL,
    last_vote_at timestamp with time zone,
    final_rank smallint,
    final_votes_count integer,
    CONSTRAINT car_event_contest_entries_final_rank_ck CHECK (((final_rank IS NULL) OR (final_rank >= 1))),
    CONSTRAINT car_event_contest_entries_reason_ck CHECK (((status <> 'rejected'::text) OR (rejection_reason IS NOT NULL))),
    CONSTRAINT car_event_contest_entries_reason_len_ck CHECK (((rejection_reason IS NULL) OR (char_length(rejection_reason) <= 300))),
    CONSTRAINT car_event_contest_entries_status_ck CHECK ((status = ANY (ARRAY['pending'::text, 'accepted'::text, 'rejected'::text, 'withdrawn'::text]))),
    CONSTRAINT car_event_contest_entries_votes_count_ck CHECK ((votes_count >= 0))
);


--
-- Name: TABLE car_event_contest_entries; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.car_event_contest_entries IS 'A car asking to be judged in a contest. The owner requests; an organizer accepts or rejects. Only accepted rows are on the ballot.';


--
-- Name: car_event_contest_votes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_event_contest_votes (
    contest_id uuid NOT NULL,
    voter_id uuid NOT NULL,
    car_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE car_event_contest_votes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.car_event_contest_votes IS 'One vote per attendee per contest. Never exposed: the API returns counts and the caller''s own choice, never who voted for what.';


--
-- Name: car_event_contests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_event_contests (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    event_id uuid NOT NULL,
    category_id text NOT NULL,
    title text NOT NULL,
    criteria text,
    status text DEFAULT 'scheduled'::text NOT NULL,
    opens_at timestamp with time zone NOT NULL,
    closes_at timestamp with time zone NOT NULL,
    finished_at timestamp with time zone,
    finished_early boolean DEFAULT false NOT NULL,
    finished_by uuid,
    votes_count integer DEFAULT 0 NOT NULL,
    entries_count integer DEFAULT 0 NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT car_event_contests_criteria_len_ck CHECK (((criteria IS NULL) OR (char_length(criteria) <= 300))),
    CONSTRAINT car_event_contests_entries_count_ck CHECK ((entries_count >= 0)),
    CONSTRAINT car_event_contests_finished_pair_ck CHECK (((status = 'finished'::text) = (finished_at IS NOT NULL))),
    CONSTRAINT car_event_contests_status_ck CHECK ((status = ANY (ARRAY['scheduled'::text, 'open'::text, 'finished'::text, 'canceled'::text]))),
    CONSTRAINT car_event_contests_title_len_ck CHECK (((char_length(title) >= 3) AND (char_length(title) <= 60))),
    CONSTRAINT car_event_contests_votes_count_ck CHECK ((votes_count >= 0)),
    CONSTRAINT car_event_contests_window_ck CHECK ((closes_at > opens_at))
);


--
-- Name: TABLE car_event_contests; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.car_event_contests IS 'A vote that runs inside a car event. scheduled = published, entries open, voting locked; open = voting; finished = results final (top 3 awarded); canceled is reserved.';


--
-- Name: car_event_organizer_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_event_organizer_rules (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    event_id uuid NOT NULL,
    rule text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    sort_order smallint NOT NULL
);


--
-- Name: TABLE car_event_organizer_rules; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.car_event_organizer_rules IS 'Rules created by the event''s organizer(s)';


--
-- Name: car_event_organizers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_event_organizers (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    event_id uuid NOT NULL,
    individual_organizer_id uuid,
    business_organizer_id uuid,
    role text DEFAULT 'organizer'::text NOT NULL,
    CONSTRAINT car_event_organizers_creator_is_individual_check CHECK (((role <> 'creator'::text) OR (individual_organizer_id IS NOT NULL))),
    CONSTRAINT car_event_organizers_exactly_one_organizer_check CHECK ((num_nonnulls(individual_organizer_id, business_organizer_id) = 1)),
    CONSTRAINT car_event_organizers_role_check CHECK ((role = ANY (ARRAY['creator'::text, 'organizer'::text])))
);


--
-- Name: TABLE car_event_organizers; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.car_event_organizers IS 'The organizers of a car event: either individual profiles or business accounts or both. At least one, can be multiple.';


--
-- Name: COLUMN car_event_organizers.individual_organizer_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.car_event_organizers.individual_organizer_id IS 'An individual account as the organizer';


--
-- Name: COLUMN car_event_organizers.business_organizer_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.car_event_organizers.business_organizer_id IS 'A business account as the event''s organizer, or one of them.';


--
-- Name: car_event_participant_status_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_event_participant_status_options (
    id text NOT NULL,
    status text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: car_event_participants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_event_participants (
    event_id uuid NOT NULL,
    owner_id uuid NOT NULL,
    car_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    status text DEFAULT 'pending'::text NOT NULL,
    withdraw_note text,
    rejection_reason text
);


--
-- Name: TABLE car_event_participants; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.car_event_participants IS 'A list with owners and their cars that are attending an event - NOT spectators';


--
-- Name: car_event_status_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_event_status_options (
    id text NOT NULL,
    status text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: car_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    event_type text NOT NULL,
    title text NOT NULL,
    description text NOT NULL,
    location_name text NOT NULL,
    starts_at timestamp with time zone NOT NULL,
    ends_at timestamp with time zone,
    cover_image_url text,
    location public.geography(Point,4326) NOT NULL,
    status text DEFAULT 'upcoming'::text NOT NULL,
    attendees_count integer DEFAULT 0 NOT NULL,
    requires_participant_approval boolean DEFAULT true NOT NULL,
    approval_status text DEFAULT 'pending'::text NOT NULL,
    attending_cars_count integer DEFAULT 0 NOT NULL,
    created_by uuid NOT NULL,
    rejection_reason text,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    max_participant_capacity integer,
    CONSTRAINT car_events_attendees_count_check CHECK ((attendees_count >= 0))
);


--
-- Name: COLUMN car_events.cover_image_url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.car_events.cover_image_url IS 'R2 object key in the MAP_EVENTS bucket (map-events/{eventId}/cover.webp), not a URL. Resolved to a public URL on read via StorageService.publicUrl.';


--
-- Name: COLUMN car_events.attendees_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.car_events.attendees_count IS 'How many spectators will be present at the event - NOT participants with their cars.';


--
-- Name: COLUMN car_events.attending_cars_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.car_events.attending_cars_count IS 'The number of attending cars to a specific event.';


--
-- Name: COLUMN car_events.rejection_reason; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.car_events.rejection_reason IS 'Why an admin rejected the event. Set when approval_status = rejected, cleared when the organizer resubmits.';


--
-- Name: COLUMN car_events.max_participant_capacity; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.car_events.max_participant_capacity IS '(Optional) Max number of participants';


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
    price_currency text,
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
-- Name: COLUMN car_modifications.price_currency; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.car_modifications.price_currency IS 'Valuta pentru price. Necesară ca totalul cheltuielilor din cartea de service să poată agrega și modurile.';


--
-- Name: car_share_links; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.car_share_links (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    car_id uuid NOT NULL,
    owner_id uuid NOT NULL,
    code text NOT NULL,
    is_enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    revoked_at timestamp with time zone,
    view_count bigint DEFAULT 0 NOT NULL,
    qr_scan_count bigint DEFAULT 0 NOT NULL,
    last_viewed_at timestamp with time zone,
    CONSTRAINT car_share_links_code_format_check CHECK ((code ~ '^[0-9A-HJKMNP-TV-Z]{10}$'::text)),
    CONSTRAINT car_share_links_qr_scan_count_check CHECK ((qr_scan_count >= 0)),
    CONSTRAINT car_share_links_view_count_check CHECK ((view_count >= 0))
);


--
-- Name: TABLE car_share_links; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.car_share_links IS 'Public share links (web URL + QR payload) for cars. One live row per car; revoked rows are kept so an old printed code answers 410 rather than 404 or, worse, another car.';


--
-- Name: COLUMN car_share_links.owner_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.car_share_links.owner_id IS 'The owner the code was issued to (the car''s garages.owner_id at creation). A code belongs to the (car, owner) pair: when the marketplace transfers a car, the live row is revoked, never re-pointed at the new owner.';


--
-- Name: COLUMN car_share_links.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.car_share_links.code IS 'Immutable random public identifier used in https://tweakdapp.com/c/{code}. 10 Crockford-base32 characters, canonical uppercase. Unique across every row ever issued. Printed on physical QR stickers: it must stay valid for as long as the owner owns the car, which is why there is no regenerate action.';


--
-- Name: COLUMN car_share_links.is_enabled; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.car_share_links.is_enabled IS 'Owner-controlled pause. false = the public endpoint answers 410, but the code stays reserved and can be re-enabled, so a printed sticker starts working again.';


--
-- Name: COLUMN car_share_links.revoked_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.car_share_links.revoked_at IS 'Set by system events only: car transferred. A revoked code answers 410 forever and is never reissued. Deleting the car or the account removes the row through the FK cascade instead.';


--
-- Name: COLUMN car_share_links.view_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.car_share_links.view_count IS 'Public page views plus in-app resolves that did not carry ?s=qr. Known crawler user-agents are not counted. Analytics only — never authoritative, and never read on a hot path.';


--
-- Name: COLUMN car_share_links.qr_scan_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.car_share_links.qr_scan_count IS 'The same, for requests carrying ?s=qr — the tag baked into the URL the QR code encodes. Separating the two is the only way to tell whether the printed sticker is doing any work.';


--
-- Name: COLUMN car_share_links.last_viewed_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.car_share_links.last_viewed_at IS 'Last counted view or scan, for the owner-facing "viewed X times, last on Y" stat.';


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
-- Name: comment_tagged_cars; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.comment_tagged_cars (
    comment_id uuid NOT NULL,
    car_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE comment_tagged_cars; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.comment_tagged_cars IS 'Cars tagged in a post comment. Deleting a car silently removes its comment tags.';


--
-- Name: comment_tagged_people; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.comment_tagged_people (
    comment_id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE comment_tagged_people; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.comment_tagged_people IS 'Profiles tagged in a post comment. A tagged car''s owner must be tagged here.';


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
-- Name: event_car_meet; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_car_meet (
    event_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    registration_deadline timestamp with time zone NOT NULL
);


--
-- Name: TABLE event_car_meet; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.event_car_meet IS 'Specific information regarding a car meet.';


--
-- Name: COLUMN event_car_meet.registration_deadline; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.event_car_meet.registration_deadline IS 'The deadline for registering as a participant to a car meet.';


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
-- Name: feedback_feed_feedback_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.feedback_feed_feedback_types (
    id text NOT NULL,
    type text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE feedback_feed_feedback_types; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.feedback_feed_feedback_types IS 'What type of feedback is it (eg: bug / feature request / feature improvement)';


--
-- Name: feedback_feed_messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.feedback_feed_messages (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    author_id uuid NOT NULL,
    message text NOT NULL,
    up_votes integer DEFAULT 0 NOT NULL,
    down_votes integer DEFAULT 0 NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    type text NOT NULL,
    status text DEFAULT 'sent'::text NOT NULL,
    net_votes integer DEFAULT 0 NOT NULL,
    staff_response_message text,
    completed_at timestamp with time zone
);


--
-- Name: TABLE feedback_feed_messages; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.feedback_feed_messages IS 'A suggestion message shared in the feedback feed by a user. It can be voted by other users.';


--
-- Name: COLUMN feedback_feed_messages.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.feedback_feed_messages.type IS 'What type of feedback is it';


--
-- Name: COLUMN feedback_feed_messages.staff_response_message; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.feedback_feed_messages.staff_response_message IS '(Optional): Message from the staff members, about the feedback suggestion.';


--
-- Name: COLUMN feedback_feed_messages.completed_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.feedback_feed_messages.completed_at IS 'When status last became ''completed''. Stamped/cleared by trg_feedback_feed_stamp_completed_at; never written by the app.';


--
-- Name: feedback_feed_status_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.feedback_feed_status_options (
    id text NOT NULL,
    status text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE feedback_feed_status_options; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.feedback_feed_status_options IS '(Reference table) All possible states for a feed feedback post';


--
-- Name: feedback_feed_votes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.feedback_feed_votes (
    user_id uuid NOT NULL,
    message_id uuid NOT NULL,
    vote_type smallint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT feedback_feed_votes_vote_type_check CHECK ((vote_type = ANY (ARRAY['-1'::integer, 1])))
);


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
-- Name: forum_thread_reply_tagged_cars; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forum_thread_reply_tagged_cars (
    reply_id uuid NOT NULL,
    car_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE forum_thread_reply_tagged_cars; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.forum_thread_reply_tagged_cars IS 'Cars tagged in a forum thread reply. Deleting a car silently removes its reply tags.';


--
-- Name: forum_thread_reply_tagged_people; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forum_thread_reply_tagged_people (
    reply_id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE forum_thread_reply_tagged_people; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.forum_thread_reply_tagged_people IS 'Profiles tagged in a forum thread reply. A tagged car''s owner must be tagged here.';


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
-- Name: forum_thread_tagged_cars; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forum_thread_tagged_cars (
    thread_id uuid NOT NULL,
    car_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE forum_thread_tagged_cars; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.forum_thread_tagged_cars IS 'Cars tagged in a forum thread (the OP). Deleting a car silently removes its thread tags.';


--
-- Name: forum_thread_tagged_people; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forum_thread_tagged_people (
    thread_id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE forum_thread_tagged_people; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.forum_thread_tagged_people IS 'Profiles tagged in a forum thread (the OP). A tagged car''s owner must be tagged here.';


--
-- Name: forum_thread_topic_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forum_thread_topic_options (
    id text NOT NULL,
    name text NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    color text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    thread_count integer DEFAULT 0 NOT NULL
);


--
-- Name: TABLE forum_thread_topic_options; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.forum_thread_topic_options IS 'Stores predefined topics for forum threads + thread_count to keep track of the most popular forums';


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
    brand_id uuid NOT NULL,
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
    updated_at timestamp with time zone DEFAULT (now() AT TIME ZONE 'utc'::text) NOT NULL,
    organized_events_enabled boolean NOT NULL,
    tags_enabled boolean DEFAULT true NOT NULL,
    service_reminders_enabled boolean DEFAULT true NOT NULL,
    event_organizer_enabled boolean DEFAULT true NOT NULL
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
-- Name: COLUMN notification_preferences.tags_enabled; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.notification_preferences.tags_enabled IS 'Whether the user receives notification if they or their cars get tagged in the app (eg: forums, thread replies, traditional posts, etc)';


--
-- Name: COLUMN notification_preferences.service_reminders_enabled; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.notification_preferences.service_reminders_enabled IS 'Reminder-e pentru service-uri programate și documente care expiră (carte de service).';


--
-- Name: COLUMN notification_preferences.event_organizer_enabled; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.notification_preferences.event_organizer_enabled IS 'Whether the user receives notifications about running events they organize (car registered, added as organizer). Distinct from organized_events_enabled, which covers attendee-facing event logistics (delays, cancellations).';


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
    reputation_score integer DEFAULT 0 NOT NULL,
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
-- Name: reputation_score_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reputation_score_history (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    reason text NOT NULL,
    score_gain integer NOT NULL,
    previous_score integer NOT NULL,
    new_score integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    source_type text,
    source_id uuid,
    source_label text,
    revoked_at timestamp with time zone,
    revoked_reason text,
    CONSTRAINT reputation_history_revocation_paired CHECK (((revoked_reason IS NULL) OR (revoked_at IS NOT NULL))),
    CONSTRAINT reputation_history_source_paired CHECK ((((source_type IS NULL) AND (source_id IS NULL)) OR ((source_type IS NOT NULL) AND (source_id IS NOT NULL)))),
    CONSTRAINT reputation_history_source_type_valid CHECK (((source_type IS NULL) OR (source_type = ANY (ARRAY['car_event'::text, 'contest'::text, 'car'::text, 'car_modification'::text, 'forum_thread'::text, 'forum_reply'::text, 'marketplace_listing'::text, 'review'::text]))))
);


--
-- Name: COLUMN reputation_score_history.source_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.reputation_score_history.source_type IS 'What kind of thing earned this, e.g. ''car_event''. NULL for entries with no source (anniversaries).';


--
-- Name: COLUMN reputation_score_history.source_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.reputation_score_history.source_id IS 'The source row''s id. Deliberately NOT a foreign key - see the migration header. Used for deep-linking.';


--
-- Name: COLUMN reputation_score_history.source_label; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.reputation_score_history.source_label IS 'The source''s display name as it was at award time. Snapshotted so the entry survives the source being renamed or deleted.';


--
-- Name: COLUMN reputation_score_history.revoked_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.reputation_score_history.revoked_at IS 'When this award was taken back. NULL = live. Revoked entries are hidden from the public timeline and excluded from the score.';


--
-- Name: COLUMN reputation_score_history.revoked_reason; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.reputation_score_history.revoked_reason IS 'Why it was taken back, e.g. ''Event was cancelled''. Shown to the owner only.';


--
-- Name: reputation_score_reason_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reputation_score_reason_options (
    id text NOT NULL,
    reason text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    points integer NOT NULL,
    category text NOT NULL,
    is_repeatable boolean DEFAULT true NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    CONSTRAINT reputation_reason_category_valid CHECK ((category = ANY (ARRAY['events'::text, 'contests'::text, 'community'::text, 'garage'::text, 'trust'::text, 'marketplace'::text, 'moderation'::text]))),
    CONSTRAINT reputation_reason_points_nonzero CHECK ((points <> 0))
);


--
-- Name: TABLE reputation_score_reason_options; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.reputation_score_reason_options IS 'Reference data: every way a user can gain or lose reputation. `id` is the code stored in reputation_score_history.reason.';


--
-- Name: COLUMN reputation_score_reason_options.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.reputation_score_reason_options.id IS 'Stable code, referenced by reputation_score_history.reason. Renaming it cascades to history.';


--
-- Name: COLUMN reputation_score_reason_options.reason; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.reputation_score_reason_options.reason IS 'Display label, e.g. "Attended a car event".';


--
-- Name: COLUMN reputation_score_reason_options.points; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.reputation_score_reason_options.points IS 'Default score delta. Negative for penalties. Never zero.';


--
-- Name: COLUMN reputation_score_reason_options.is_repeatable; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.reputation_score_reason_options.is_repeatable IS 'false = one-time achievement; the backend refuses a second award for the same user.';


--
-- Name: COLUMN reputation_score_reason_options.is_active; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.reputation_score_reason_options.is_active IS 'Retire a reason by setting this false — never DELETE, or you orphan history.';


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
-- Name: user_badges; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_badges (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    badge_id text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    granted_by uuid,
    granted_in_app boolean DEFAULT false NOT NULL
);


--
-- Name: TABLE user_badges; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.user_badges IS 'Which badges a user has unlocked. One row per (user, badge) — enforced by user_badges_user_badge_uq, which is what makes awarding idempotent. Revoking deletes the row; there is no tombstone.';


--
-- Name: COLUMN user_badges.granted_by; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.user_badges.granted_by IS 'The staff member who granted this by hand, from admin_team_members. NULL = awarded automatically by the backend. Not an FK: staff and app users live in different tables, and removing a staff member must not disturb the badges they granted.';


--
-- Name: COLUMN user_badges.granted_in_app; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.user_badges.granted_in_app IS 'Whether the badge animation was shown in the app.';


--
-- Name: user_devices_firebase_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_devices_firebase_token (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    token text NOT NULL,
    platform text NOT NULL,
    app_version text,
    locale text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT user_devices_firebase_token_app_version_chk CHECK (((app_version IS NULL) OR ((length(app_version) >= 1) AND (length(app_version) <= 32)))),
    CONSTRAINT user_devices_firebase_token_locale_chk CHECK (((locale IS NULL) OR (locale ~ '^[a-zA-Z]{2,3}([-_][a-zA-Z0-9]{2,8}){0,2}$'::text))),
    CONSTRAINT user_devices_firebase_token_platform_chk CHECK ((platform = ANY (ARRAY['ios'::text, 'android'::text]))),
    CONSTRAINT user_devices_firebase_token_token_chk CHECK (((length(token) >= 32) AND (length(token) <= 512)))
);


--
-- Name: TABLE user_devices_firebase_token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.user_devices_firebase_token IS 'FCM registration tokens, one row per device. Single source of truth shared by the Spring backend (all notification types except dm) and the Supabase edge function (dm). token is UNIQUE on its own, not (user_id, token): re-registering an existing token reassigns it to the calling user, which is how device handoff between accounts works. Never exposed over the REST API - there is no list-devices endpoint and no response ever contains a token.';


--
-- Name: COLUMN user_devices_firebase_token.token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.user_devices_firebase_token.token IS 'FCM registration token. A device-addressable secret: anyone holding it can push arbitrary notifications to that device. RLS-denied and revoked from anon/authenticated so no Supabase client can enumerate tokens.';


--
-- Name: COLUMN user_devices_firebase_token.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.user_devices_firebase_token.updated_at IS 'Bumped on every re-registration by the touch trigger. Doubles as the staleness watermark: FCM garbage-collects registrations after 270 days of inactivity.';


--
-- Name: user_presence; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_presence (
    user_id uuid NOT NULL,
    last_seen_at timestamp with time zone NOT NULL
);


--
-- Name: vehicle_entry_attachments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vehicle_entry_attachments (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    entry_id uuid NOT NULL,
    file_key text NOT NULL,
    file_type text NOT NULL,
    "position" smallint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT vehicle_entry_attachments_file_type_check CHECK ((file_type = ANY (ARRAY['image'::text, 'pdf'::text])))
);


--
-- Name: TABLE vehicle_entry_attachments; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.vehicle_entry_attachments IS 'Facturi, bonuri și scanuri de polițe atașate unei intrări din cartea de service. file_key = cheie Cloudflare R2, nu URL.';


--
-- Name: vehicle_entry_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vehicle_entry_options (
    id text NOT NULL,
    name text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    kind text DEFAULT 'service'::text NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    CONSTRAINT vehicle_entry_options_kind_check CHECK ((kind = ANY (ARRAY['service'::text, 'document'::text])))
);


--
-- Name: TABLE vehicle_entry_options; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.vehicle_entry_options IS 'Reference table storing a list of predefined services on a car (eg: oil change, tire rotation & balance, RCA renewal)';


--
-- Name: COLUMN vehicle_entry_options.kind; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.vehicle_entry_options.kind IS 'service = intervenție pe mașină (ulei, frâne, tuning); document = poliță/inspecție (RCA, CASCO, ITP, rovinietă)';


--
-- Name: vehicle_entry_status; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vehicle_entry_status (
    id text NOT NULL,
    status text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    color text
);


--
-- Name: vehicle_history_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vehicle_history_entries (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    scheduled_date timestamp with time zone,
    title text,
    category text NOT NULL,
    price numeric(12,2),
    location text,
    business_name text,
    completed_mileage integer,
    notes text,
    repeatable boolean DEFAULT false NOT NULL,
    cycle_length integer,
    notify_before integer,
    car_id uuid NOT NULL,
    price_currency text,
    status text DEFAULT 'scheduled'::text NOT NULL,
    entry_kind text DEFAULT 'service'::text NOT NULL,
    valid_from date,
    policy_number text,
    trigger_type text DEFAULT 'date'::text NOT NULL,
    due_mileage integer,
    completed_at timestamp with time zone,
    mileage_unit_id text,
    cycle_mileage integer,
    parent_entry_id uuid,
    advance_reminder_sent_at timestamp with time zone,
    due_day_reminder_sent_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    business_profile_id uuid,
    CONSTRAINT vehicle_history_entries_cycle_length_check CHECK ((cycle_length > 0)),
    CONSTRAINT vhe_completed_mileage_check CHECK (((completed_mileage IS NULL) OR (completed_mileage >= 0))),
    CONSTRAINT vhe_cycle_mileage_check CHECK (((cycle_mileage IS NULL) OR (cycle_mileage > 0))),
    CONSTRAINT vhe_document_shape_check CHECK (((entry_kind <> 'document'::text) OR ((trigger_type = 'date'::text) AND (scheduled_date IS NOT NULL)))),
    CONSTRAINT vhe_due_mileage_check CHECK (((due_mileage IS NULL) OR (due_mileage >= 0))),
    CONSTRAINT vhe_entry_kind_check CHECK ((entry_kind = ANY (ARRAY['service'::text, 'document'::text]))),
    CONSTRAINT vhe_notify_before_check CHECK (((notify_before IS NULL) OR (notify_before > 0))),
    CONSTRAINT vhe_price_check CHECK (((price IS NULL) OR (price >= (0)::numeric))),
    CONSTRAINT vhe_price_currency_check CHECK (((price IS NULL) OR (price_currency IS NOT NULL))),
    CONSTRAINT vhe_repeatable_check CHECK (((repeatable = false) OR (cycle_length IS NOT NULL) OR (cycle_mileage IS NOT NULL))),
    CONSTRAINT vhe_trigger_consistency_check CHECK ((((trigger_type = 'date'::text) AND (scheduled_date IS NOT NULL)) OR ((trigger_type = 'mileage'::text) AND (due_mileage IS NOT NULL)) OR ((trigger_type = 'both'::text) AND (scheduled_date IS NOT NULL) AND (due_mileage IS NOT NULL)))),
    CONSTRAINT vhe_trigger_type_check CHECK ((trigger_type = ANY (ARRAY['date'::text, 'mileage'::text, 'both'::text])))
);


--
-- Name: TABLE vehicle_history_entries; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.vehicle_history_entries IS 'Stores each entry on a car (eg: oil change, ITP, tire rotation, etc)';


--
-- Name: COLUMN vehicle_history_entries.completed_mileage; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.vehicle_history_entries.completed_mileage IS 'Kilometrajul mașinii în momentul efectuării. Unitatea vine din mileage_unit_id.';


--
-- Name: COLUMN vehicle_history_entries.cycle_length; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.vehicle_history_entries.cycle_length IS 'An integer number, storing the number of months, representing a cycle''s length.';


--
-- Name: COLUMN vehicle_history_entries.notify_before; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.vehicle_history_entries.notify_before IS 'The number of days before receiving the reminder notification';


--
-- Name: COLUMN vehicle_history_entries.valid_from; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.vehicle_history_entries.valid_from IS 'Doar pentru entry_kind = document: data de început a poliței. Data de expirare se ține în scheduled_date.';


--
-- Name: COLUMN vehicle_history_entries.policy_number; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.vehicle_history_entries.policy_number IS 'Doar pentru entry_kind = document: numărul poliței / documentului (opțional).';


--
-- Name: COLUMN vehicle_history_entries.completed_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.vehicle_history_entries.completed_at IS 'Data reală la care s-a efectuat intervenția. NULL cât timp status = scheduled.';


--
-- Name: COLUMN vehicle_history_entries.parent_entry_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.vehicle_history_entries.parent_entry_id IS 'Intrarea din care a fost generat acest rând (lanț de recurență). NULL pentru prima apariție.';


--
-- Name: COLUMN vehicle_history_entries.advance_reminder_sent_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.vehicle_history_entries.advance_reminder_sent_at IS 'Setat de backend când a plecat push-ul cu notify_before zile înainte. Fără el, job-ul retrimite la fiecare rulare.';


--
-- Name: COLUMN vehicle_history_entries.due_day_reminder_sent_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.vehicle_history_entries.due_day_reminder_sent_at IS 'Setat când a plecat reminder-ul din dimineața scadenței.';


--
-- Name: COLUMN vehicle_history_entries.business_profile_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.vehicle_history_entries.business_profile_id IS 'Opțional: contul de business din app. business_name rămâne fallback text liber.';


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
-- Name: admin_team_members_single_owner_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX admin_team_members_single_owner_idx ON public.admin_team_members USING btree (((true))) WHERE (role = 'owner'::text);


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
-- Name: badges badges_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.badges
    ADD CONSTRAINT badges_pkey PRIMARY KEY (id);


--
-- Name: blocked_accounts blocked_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blocked_accounts
    ADD CONSTRAINT blocked_accounts_pkey PRIMARY KEY (blocker_id, blocked_id);


--
-- Name: business_account_active_status_options business_account_active_status_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_account_active_status_options
    ADD CONSTRAINT business_account_active_status_options_pkey PRIMARY KEY (id);


--
-- Name: business_account_active_status_options business_account_active_status_options_status_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_account_active_status_options
    ADD CONSTRAINT business_account_active_status_options_status_key UNIQUE (status);


--
-- Name: business_account_verification_status_options business_account_verification_status_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_account_verification_status_options
    ADD CONSTRAINT business_account_verification_status_options_pkey PRIMARY KEY (id);


--
-- Name: business_account_verification_status_options business_account_verification_status_options_status_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_account_verification_status_options
    ADD CONSTRAINT business_account_verification_status_options_status_key UNIQUE (status);


--
-- Name: business_accounts business_accounts_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_accounts
    ADD CONSTRAINT business_accounts_email_key UNIQUE (email);


--
-- Name: business_accounts business_accounts_phone_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_accounts
    ADD CONSTRAINT business_accounts_phone_number_key UNIQUE (phone_number);


--
-- Name: business_accounts business_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_accounts
    ADD CONSTRAINT business_accounts_pkey PRIMARY KEY (id);


--
-- Name: business_hours business_hours_business_weekday_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_hours
    ADD CONSTRAINT business_hours_business_weekday_key UNIQUE (business_id, weekday);


--
-- Name: business_hours business_hours_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_hours
    ADD CONSTRAINT business_hours_pkey PRIMARY KEY (id);


--
-- Name: business_type_options business_type_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_type_options
    ADD CONSTRAINT business_type_options_pkey PRIMARY KEY (id);


--
-- Name: business_type_options business_type_options_type_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_type_options
    ADD CONSTRAINT business_type_options_type_key UNIQUE (type);


--
-- Name: car_brands car_brands_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_brands
    ADD CONSTRAINT car_brands_pkey PRIMARY KEY (id);


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
-- Name: car_event_approval_status_options car_event_approval_status_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_approval_status_options
    ADD CONSTRAINT car_event_approval_status_options_pkey PRIMARY KEY (id);


--
-- Name: car_event_approval_status_options car_event_approval_status_options_status_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_approval_status_options
    ADD CONSTRAINT car_event_approval_status_options_status_key UNIQUE (status);


--
-- Name: car_event_attendee_status car_event_attendee_status_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_attendee_status
    ADD CONSTRAINT car_event_attendee_status_pkey PRIMARY KEY (id);


--
-- Name: car_event_attendee_status car_event_attendee_status_status_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_attendee_status
    ADD CONSTRAINT car_event_attendee_status_status_key UNIQUE (status);


--
-- Name: car_event_attendees_list car_event_attendees_list_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_attendees_list
    ADD CONSTRAINT car_event_attendees_list_pkey PRIMARY KEY (user_id, event_id);


--
-- Name: car_event_organizer_rules car_event_organizer_rules_event_position_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_organizer_rules
    ADD CONSTRAINT car_event_organizer_rules_event_position_unique UNIQUE (event_id, sort_order);


--
-- Name: car_event_organizer_rules car_event_organizer_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_organizer_rules
    ADD CONSTRAINT car_event_organizer_rules_pkey PRIMARY KEY (id);


--
-- Name: car_event_organizers car_event_organizers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_organizers
    ADD CONSTRAINT car_event_organizers_pkey PRIMARY KEY (id);


--
-- Name: car_event_participant_status_options car_event_participant_status_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_participant_status_options
    ADD CONSTRAINT car_event_participant_status_options_pkey PRIMARY KEY (id);


--
-- Name: car_event_participants car_event_participants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_participants
    ADD CONSTRAINT car_event_participants_pkey PRIMARY KEY (event_id, car_id);


--
-- Name: car_event_status_options car_event_status_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_status_options
    ADD CONSTRAINT car_event_status_options_pkey PRIMARY KEY (id);


--
-- Name: car_event_status_options car_event_status_options_status_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_status_options
    ADD CONSTRAINT car_event_status_options_status_key UNIQUE (status);


--
-- Name: car_events car_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_events
    ADD CONSTRAINT car_events_pkey PRIMARY KEY (id);


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
-- Name: car_share_links car_share_links_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_share_links
    ADD CONSTRAINT car_share_links_pkey PRIMARY KEY (id);


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
-- Name: comment_tagged_cars comment_tagged_cars_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comment_tagged_cars
    ADD CONSTRAINT comment_tagged_cars_pkey PRIMARY KEY (comment_id, car_id);


--
-- Name: comment_tagged_people comment_tagged_people_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comment_tagged_people
    ADD CONSTRAINT comment_tagged_people_pkey PRIMARY KEY (comment_id, user_id);


--
-- Name: comments comments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comments
    ADD CONSTRAINT comments_pkey PRIMARY KEY (id);


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
-- Name: event_car_meet event_car_meet_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_car_meet
    ADD CONSTRAINT event_car_meet_pkey PRIMARY KEY (event_id);


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
-- Name: feedback_feed_feedback_types feedback_feed_feedback_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_feed_feedback_types
    ADD CONSTRAINT feedback_feed_feedback_types_pkey PRIMARY KEY (id);


--
-- Name: feedback_feed_messages feedback_feed_message_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_feed_messages
    ADD CONSTRAINT feedback_feed_message_pkey PRIMARY KEY (id);


--
-- Name: feedback_feed_status_options feedback_feed_status_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_feed_status_options
    ADD CONSTRAINT feedback_feed_status_options_pkey PRIMARY KEY (id);


--
-- Name: feedback_feed_votes feedback_feed_votes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_feed_votes
    ADD CONSTRAINT feedback_feed_votes_pkey PRIMARY KEY (user_id, message_id);


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
-- Name: forum_thread_reply_tagged_cars forum_thread_reply_tagged_cars_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_reply_tagged_cars
    ADD CONSTRAINT forum_thread_reply_tagged_cars_pkey PRIMARY KEY (reply_id, car_id);


--
-- Name: forum_thread_reply_tagged_people forum_thread_reply_tagged_people_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_reply_tagged_people
    ADD CONSTRAINT forum_thread_reply_tagged_people_pkey PRIMARY KEY (reply_id, user_id);


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
-- Name: forum_thread_tagged_cars forum_thread_tagged_cars_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_tagged_cars
    ADD CONSTRAINT forum_thread_tagged_cars_pkey PRIMARY KEY (thread_id, car_id);


--
-- Name: forum_thread_tagged_people forum_thread_tagged_people_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_tagged_people
    ADD CONSTRAINT forum_thread_tagged_people_pkey PRIMARY KEY (thread_id, user_id);


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
-- Name: forum_thread_topic_options forum_topics_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_topic_options
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
-- Name: car_event_categories map_event_categories_category_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_categories
    ADD CONSTRAINT map_event_categories_category_key UNIQUE (category);


--
-- Name: car_event_categories map_event_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_categories
    ADD CONSTRAINT map_event_categories_pkey PRIMARY KEY (id);


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
-- Name: reputation_score_history reputation_score_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reputation_score_history
    ADD CONSTRAINT reputation_score_history_pkey PRIMARY KEY (id);


--
-- Name: reputation_score_reason_options reputation_score_reason_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reputation_score_reason_options
    ADD CONSTRAINT reputation_score_reason_options_pkey PRIMARY KEY (id);


--
-- Name: saved_posts saved_posts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.saved_posts
    ADD CONSTRAINT saved_posts_pkey PRIMARY KEY (user_id, post_id);


--
-- Name: vehicle_entry_options service_type_options_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicle_entry_options
    ADD CONSTRAINT service_type_options_name_key UNIQUE (name);


--
-- Name: vehicle_entry_options service_type_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicle_entry_options
    ADD CONSTRAINT service_type_options_pkey PRIMARY KEY (id);


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
-- Name: user_badges user_badges_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_badges
    ADD CONSTRAINT user_badges_pkey PRIMARY KEY (id);


--
-- Name: user_devices_firebase_token user_devices_firebase_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_devices_firebase_token
    ADD CONSTRAINT user_devices_firebase_token_pkey PRIMARY KEY (id);


--
-- Name: user_devices_firebase_token user_devices_firebase_token_token_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_devices_firebase_token
    ADD CONSTRAINT user_devices_firebase_token_token_key UNIQUE (token);


--
-- Name: user_presence user_presence_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_presence
    ADD CONSTRAINT user_presence_pkey PRIMARY KEY (user_id);


--
-- Name: vehicle_entry_attachments vehicle_entry_attachments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicle_entry_attachments
    ADD CONSTRAINT vehicle_entry_attachments_pkey PRIMARY KEY (id);


--
-- Name: vehicle_entry_status vehicle_entry_status_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicle_entry_status
    ADD CONSTRAINT vehicle_entry_status_pkey PRIMARY KEY (id);


--
-- Name: vehicle_entry_status vehicle_entry_status_status_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicle_entry_status
    ADD CONSTRAINT vehicle_entry_status_status_key UNIQUE (status);


--
-- Name: vehicle_history_entries vehicle_history_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicle_history_entries
    ADD CONSTRAINT vehicle_history_entries_pkey PRIMARY KEY (id);


--
-- Name: business_accounts_location_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX business_accounts_location_idx ON public.business_accounts USING gist (location);


--
-- Name: business_accounts_pending_review_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX business_accounts_pending_review_idx ON public.business_accounts USING btree (created_at, id) WHERE (verification_status = 'pending'::text);


--
-- Name: feedback_feed_messages_new_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX feedback_feed_messages_new_idx ON public.feedback_feed_messages USING btree (created_at DESC, id DESC) WHERE ((status = 'sent'::text) AND (is_deleted = false));


--
-- Name: business_accounts_visible_type_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX business_accounts_visible_type_idx ON public.business_accounts USING btree (type) WHERE ((active_status = 'active'::text) AND (verification_status = 'verified'::text));


--
-- Name: business_hours_business_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX business_hours_business_id_idx ON public.business_hours USING btree (business_id);


--
-- Name: car_event_attendees_list_event_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX car_event_attendees_list_event_id_idx ON public.car_event_attendees_list USING btree (event_id);


--
-- Name: car_event_attendees_list_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX car_event_attendees_list_status_idx ON public.car_event_attendees_list USING btree (status);


--
-- Name: car_event_organizers_individual_organizer_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX car_event_organizers_individual_organizer_id_idx ON public.car_event_organizers USING btree (individual_organizer_id);


--
-- Name: car_event_organizers_one_creator; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX car_event_organizers_one_creator ON public.car_event_organizers USING btree (event_id) WHERE (role = 'creator'::text);


--
-- Name: car_event_organizers_unique_business; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX car_event_organizers_unique_business ON public.car_event_organizers USING btree (event_id, business_organizer_id) WHERE (business_organizer_id IS NOT NULL);


--
-- Name: car_event_organizers_unique_individual; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX car_event_organizers_unique_individual ON public.car_event_organizers USING btree (event_id, individual_organizer_id) WHERE (individual_organizer_id IS NOT NULL);


--
-- Name: car_event_participants_owner_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX car_event_participants_owner_id_idx ON public.car_event_participants USING btree (owner_id);


--
-- Name: car_events_created_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX car_events_created_at_idx ON public.car_events USING btree (created_at);


--
-- Name: car_events_created_by_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX car_events_created_by_idx ON public.car_events USING btree (created_by);


--
-- Name: car_events_location_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX car_events_location_idx ON public.car_events USING gist (location);


--
-- Name: car_events_map_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX car_events_map_idx ON public.car_events USING btree (approval_status, status, starts_at);


--
-- Name: car_events_starts_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX car_events_starts_at_idx ON public.car_events USING btree (starts_at);


--
-- Name: car_share_links_active_car_uq; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX car_share_links_active_car_uq ON public.car_share_links USING btree (car_id) WHERE (revoked_at IS NULL);


--
-- Name: car_share_links_code_uq; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX car_share_links_code_uq ON public.car_share_links USING btree (code);


--
-- Name: car_share_links_owner_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX car_share_links_owner_idx ON public.car_share_links USING btree (owner_id);


--
-- Name: dm_conversations_user_b_a_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX dm_conversations_user_b_a_idx ON public.dm_conversations USING btree (user_b, user_a);


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
-- Name: feedback_feed_messages_created_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX feedback_feed_messages_created_at_idx ON public.feedback_feed_messages USING btree (created_at);


--
-- Name: feedback_status_options_sort_order_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX feedback_status_options_sort_order_idx ON public.feedback_status_options USING btree (sort_order);


--
-- Name: feedback_subscriptions_user_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX feedback_subscriptions_user_idx ON public.feedback_subscriptions USING btree (user_id);


--
-- Name: feedback_votes_recent_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX feedback_votes_recent_idx ON public.feedback_votes USING btree (feedback_id, created_at);


--
-- Name: forum_shortcuts_user_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX forum_shortcuts_user_id_idx ON public.forum_shortcuts USING btree (user_id);


--
-- Name: forum_thread_topics_thread_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX forum_thread_topics_thread_id_idx ON public.forum_thread_topics USING btree (thread_id);


--
-- Name: forum_topics_thread_count_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX forum_topics_thread_count_idx ON public.forum_thread_topic_options USING btree (thread_count);


--
-- Name: idx_car_event_organizer_rules_event_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_car_event_organizer_rules_event_id ON public.car_event_organizer_rules USING btree (event_id);


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
-- Name: idx_comment_tagged_cars_car_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comment_tagged_cars_car_id ON public.comment_tagged_cars USING btree (car_id);


--
-- Name: idx_comment_tagged_people_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comment_tagged_people_user_id ON public.comment_tagged_people USING btree (user_id);


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
-- Name: idx_feedback_feed_messages_active_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_feedback_feed_messages_active_created ON public.feedback_feed_messages USING btree (created_at DESC, id DESC) WHERE (status <> 'completed'::text);


--
-- Name: idx_feedback_feed_messages_completed_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_feedback_feed_messages_completed_created ON public.feedback_feed_messages USING btree (created_at DESC, id DESC) WHERE (status = 'completed'::text);


--
-- Name: idx_feedback_feed_messages_completed_shipped; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_feedback_feed_messages_completed_shipped ON public.feedback_feed_messages USING btree (completed_at DESC, id DESC) WHERE (status = 'completed'::text);


--
-- Name: idx_feedback_feed_messages_popular; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_feedback_feed_messages_popular ON public.feedback_feed_messages USING btree (net_votes DESC, id DESC) WHERE (status <> 'completed'::text);


--
-- Name: idx_feedback_feed_votes_message; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_feedback_feed_votes_message ON public.feedback_feed_votes USING btree (message_id);


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
-- Name: idx_forum_thread_reply_tagged_cars_car_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_forum_thread_reply_tagged_cars_car_id ON public.forum_thread_reply_tagged_cars USING btree (car_id);


--
-- Name: idx_forum_thread_reply_tagged_people_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_forum_thread_reply_tagged_people_user_id ON public.forum_thread_reply_tagged_people USING btree (user_id);


--
-- Name: idx_forum_thread_saves_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_forum_thread_saves_user ON public.forum_thread_saves USING btree (user_id, created_at DESC, thread_id DESC);


--
-- Name: idx_forum_thread_tagged_cars_car_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_forum_thread_tagged_cars_car_id ON public.forum_thread_tagged_cars USING btree (car_id);


--
-- Name: idx_forum_thread_tagged_people_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_forum_thread_tagged_people_user_id ON public.forum_thread_tagged_people USING btree (user_id);


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
-- Name: idx_vehicle_entry_attachments_entry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vehicle_entry_attachments_entry ON public.vehicle_entry_attachments USING btree (entry_id, "position");


--
-- Name: idx_vhe_car_completed; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vhe_car_completed ON public.vehicle_history_entries USING btree (car_id, completed_at DESC);


--
-- Name: idx_vhe_car_due_mileage; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vhe_car_due_mileage ON public.vehicle_history_entries USING btree (car_id, due_mileage) WHERE ((status = 'scheduled'::text) AND (due_mileage IS NOT NULL));


--
-- Name: idx_vhe_car_scheduled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vhe_car_scheduled ON public.vehicle_history_entries USING btree (car_id, scheduled_date) WHERE (status = 'scheduled'::text);


--
-- Name: idx_vhe_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vhe_parent ON public.vehicle_history_entries USING btree (parent_entry_id) WHERE (parent_entry_id IS NOT NULL);


--
-- Name: idx_vhe_pending_reminders; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vhe_pending_reminders ON public.vehicle_history_entries USING btree (scheduled_date) WHERE ((status = 'scheduled'::text) AND (advance_reminder_sent_at IS NULL));


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
-- Name: notifications_user_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX notifications_user_id_idx ON public.notifications USING btree (user_id);


--
-- Name: notifications_user_unread_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX notifications_user_unread_idx ON public.notifications USING btree (user_id) WHERE (NOT is_read);


--
-- Name: reputation_score_history_source_uq; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX reputation_score_history_source_uq ON public.reputation_score_history USING btree (user_id, reason, source_type, source_id) WHERE ((source_id IS NOT NULL) AND (revoked_at IS NULL));


--
-- Name: reputation_score_history_user_created_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX reputation_score_history_user_created_idx ON public.reputation_score_history USING btree (user_id, created_at DESC, id DESC);


--
-- Name: reputation_score_history_user_live_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX reputation_score_history_user_live_idx ON public.reputation_score_history USING btree (user_id, created_at DESC, id DESC) WHERE (revoked_at IS NULL);


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
-- Name: user_badges_badge_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX user_badges_badge_id_idx ON public.user_badges USING btree (badge_id);


--
-- Name: user_badges_user_badge_uq; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX user_badges_user_badge_uq ON public.user_badges USING btree (user_id, badge_id);


--
-- Name: user_devices_firebase_token_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX user_devices_firebase_token_updated_at_idx ON public.user_devices_firebase_token USING btree (updated_at);


--
-- Name: user_devices_firebase_token_user_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX user_devices_firebase_token_user_id_idx ON public.user_devices_firebase_token USING btree (user_id);


--
-- Name: comment_reports comment_reports_case; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER comment_reports_case AFTER INSERT ON public.comment_reports FOR EACH ROW EXECUTE FUNCTION public.upsert_moderation_case('comment', 'comment_id');


--
-- Name: feedback_comments feedback_comments_count; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER feedback_comments_count AFTER INSERT OR DELETE ON public.feedback_comments FOR EACH ROW EXECUTE FUNCTION public.bump_feedback_comment_count();


--
-- Name: feedback_feed_messages feedback_feed_messages_notify_author_trg; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER feedback_feed_messages_notify_author_trg AFTER UPDATE OF status ON public.feedback_feed_messages FOR EACH ROW EXECUTE FUNCTION public.trg_feedback_feed_notify_author_status_change();


--
-- Name: feedback_feed_messages feedback_feed_messages_stamp_completed_at_trg; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER feedback_feed_messages_stamp_completed_at_trg BEFORE INSERT OR UPDATE OF status ON public.feedback_feed_messages FOR EACH ROW EXECUTE FUNCTION public.trg_feedback_feed_stamp_completed_at();


--
-- Name: feedback_feed_votes feedback_feed_votes_block_completed_trg; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER feedback_feed_votes_block_completed_trg BEFORE INSERT OR DELETE OR UPDATE ON public.feedback_feed_votes FOR EACH ROW EXECUTE FUNCTION public.trg_feedback_feed_block_completed_votes();


--
-- Name: feedback_feed_votes feedback_feed_votes_counts_trg; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER feedback_feed_votes_counts_trg AFTER INSERT OR DELETE OR UPDATE ON public.feedback_feed_votes FOR EACH ROW EXECUTE FUNCTION public.trg_feedback_feed_update_vote_counts();


--
-- Name: feedback_votes feedback_votes_count; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER feedback_votes_count AFTER INSERT OR DELETE ON public.feedback_votes FOR EACH ROW EXECUTE FUNCTION public.bump_feedback_vote_count();


--
-- Name: notifications notifications_dm_push; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER notifications_dm_push AFTER INSERT ON public.notifications FOR EACH ROW WHEN ((new.type = 'dm'::text)) EXECUTE FUNCTION public.dm_push_notify();


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
-- Name: car_event_attendees_list trg_update_car_event_attendees_count; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_update_car_event_attendees_count AFTER INSERT OR DELETE OR UPDATE ON public.car_event_attendees_list FOR EACH ROW EXECUTE FUNCTION public.update_car_event_attendees_count();


--
-- Name: car_event_participants trg_update_car_event_attending_cars_count; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_update_car_event_attending_cars_count AFTER INSERT OR DELETE OR UPDATE ON public.car_event_participants FOR EACH ROW EXECUTE FUNCTION public.update_car_event_attending_cars_count();


--
-- Name: vehicle_history_entries trg_vehicle_history_entries_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_vehicle_history_entries_updated_at BEFORE UPDATE ON public.vehicle_history_entries FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: user_devices_firebase_token user_devices_firebase_token_touch_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER user_devices_firebase_token_touch_updated_at BEFORE UPDATE ON public.user_devices_firebase_token FOR EACH ROW EXECUTE FUNCTION public.user_devices_firebase_token_touch();


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
-- Name: business_accounts business_accounts_active_status_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_accounts
    ADD CONSTRAINT business_accounts_active_status_fkey FOREIGN KEY (active_status) REFERENCES public.business_account_active_status_options(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: business_accounts business_accounts_city_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_accounts
    ADD CONSTRAINT business_accounts_city_fkey FOREIGN KEY (city) REFERENCES public.cities(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: business_accounts business_accounts_type_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_accounts
    ADD CONSTRAINT business_accounts_type_fkey FOREIGN KEY (type) REFERENCES public.business_type_options(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: business_accounts business_accounts_verification_status_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_accounts
    ADD CONSTRAINT business_accounts_verification_status_fkey FOREIGN KEY (verification_status) REFERENCES public.business_account_verification_status_options(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: business_hours business_hours_business_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_hours
    ADD CONSTRAINT business_hours_business_id_fkey FOREIGN KEY (business_id) REFERENCES public.business_accounts(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: car_event_attendees_list car_event_attendees_list_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_attendees_list
    ADD CONSTRAINT car_event_attendees_list_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.car_events(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: car_event_attendees_list car_event_attendees_list_status_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_attendees_list
    ADD CONSTRAINT car_event_attendees_list_status_fkey FOREIGN KEY (status) REFERENCES public.car_event_attendee_status(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: car_event_attendees_list car_event_attendees_list_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_attendees_list
    ADD CONSTRAINT car_event_attendees_list_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: car_event_organizer_rules car_event_organizer_rules_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_organizer_rules
    ADD CONSTRAINT car_event_organizer_rules_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.car_events(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: car_event_organizers car_event_organizers_business_organizer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_organizers
    ADD CONSTRAINT car_event_organizers_business_organizer_id_fkey FOREIGN KEY (business_organizer_id) REFERENCES public.business_accounts(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: car_event_organizers car_event_organizers_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_organizers
    ADD CONSTRAINT car_event_organizers_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.car_events(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: car_event_organizers car_event_organizers_individual_organizer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_organizers
    ADD CONSTRAINT car_event_organizers_individual_organizer_id_fkey FOREIGN KEY (individual_organizer_id) REFERENCES public.profiles(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: car_event_participants car_event_participants_car_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_participants
    ADD CONSTRAINT car_event_participants_car_id_fkey FOREIGN KEY (car_id) REFERENCES public.cars(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: car_event_participants car_event_participants_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_participants
    ADD CONSTRAINT car_event_participants_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.car_events(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: car_event_participants car_event_participants_owner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_participants
    ADD CONSTRAINT car_event_participants_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES public.profiles(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: car_event_participants car_event_participants_status_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_participants
    ADD CONSTRAINT car_event_participants_status_fkey FOREIGN KEY (status) REFERENCES public.car_event_participant_status_options(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: car_events car_events_approval_status_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_events
    ADD CONSTRAINT car_events_approval_status_fkey FOREIGN KEY (approval_status) REFERENCES public.car_event_approval_status_options(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: car_events car_events_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_events
    ADD CONSTRAINT car_events_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.profiles(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: car_events car_events_event_type_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_events
    ADD CONSTRAINT car_events_event_type_fkey FOREIGN KEY (event_type) REFERENCES public.car_event_categories(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: car_events car_events_status_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_events
    ADD CONSTRAINT car_events_status_fkey FOREIGN KEY (status) REFERENCES public.car_event_status_options(id) ON UPDATE CASCADE ON DELETE RESTRICT;


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
-- Name: car_modifications car_modifications_price_currency_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_modifications
    ADD CONSTRAINT car_modifications_price_currency_fkey FOREIGN KEY (price_currency) REFERENCES public.price_currencies_options(id);


--
-- Name: car_share_links car_share_links_car_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_share_links
    ADD CONSTRAINT car_share_links_car_id_fkey FOREIGN KEY (car_id) REFERENCES public.cars(id) ON DELETE CASCADE;


--
-- Name: car_share_links car_share_links_owner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_share_links
    ADD CONSTRAINT car_share_links_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


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
-- Name: comment_tagged_cars comment_tagged_cars_car_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comment_tagged_cars
    ADD CONSTRAINT comment_tagged_cars_car_id_fkey FOREIGN KEY (car_id) REFERENCES public.cars(id) ON DELETE CASCADE;


--
-- Name: comment_tagged_cars comment_tagged_cars_comment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comment_tagged_cars
    ADD CONSTRAINT comment_tagged_cars_comment_id_fkey FOREIGN KEY (comment_id) REFERENCES public.comments(id) ON DELETE CASCADE;


--
-- Name: comment_tagged_people comment_tagged_people_comment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comment_tagged_people
    ADD CONSTRAINT comment_tagged_people_comment_id_fkey FOREIGN KEY (comment_id) REFERENCES public.comments(id) ON DELETE CASCADE;


--
-- Name: comment_tagged_people comment_tagged_people_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comment_tagged_people
    ADD CONSTRAINT comment_tagged_people_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


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
-- Name: event_car_meet event_car_meet_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_car_meet
    ADD CONSTRAINT event_car_meet_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.car_events(id) ON UPDATE CASCADE ON DELETE CASCADE;


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
-- Name: feedback_feed_messages feedback_feed_message_author_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_feed_messages
    ADD CONSTRAINT feedback_feed_message_author_id_fkey FOREIGN KEY (author_id) REFERENCES public.profiles(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: feedback_feed_messages feedback_feed_message_type_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_feed_messages
    ADD CONSTRAINT feedback_feed_message_type_fkey FOREIGN KEY (type) REFERENCES public.feedback_feed_feedback_types(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: feedback_feed_messages feedback_feed_messages_status_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_feed_messages
    ADD CONSTRAINT feedback_feed_messages_status_fkey FOREIGN KEY (status) REFERENCES public.feedback_feed_status_options(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: feedback_feed_votes feedback_feed_votes_message_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_feed_votes
    ADD CONSTRAINT feedback_feed_votes_message_id_fkey FOREIGN KEY (message_id) REFERENCES public.feedback_feed_messages(id) ON DELETE CASCADE;


--
-- Name: feedback_feed_votes feedback_feed_votes_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedback_feed_votes
    ADD CONSTRAINT feedback_feed_votes_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;


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
    ADD CONSTRAINT forum_shortcuts_topic_id_fkey FOREIGN KEY (topic_id) REFERENCES public.forum_thread_topic_options(id);


--
-- Name: forum_shortcuts forum_shortcuts_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_shortcuts
    ADD CONSTRAINT forum_shortcuts_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: forum_thread_likes forum_thread_likes_thread_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_likes
    ADD CONSTRAINT forum_thread_likes_thread_id_fkey FOREIGN KEY (thread_id) REFERENCES public.forum_threads(id) ON DELETE CASCADE;


--
-- Name: forum_thread_likes forum_thread_likes_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_likes
    ADD CONSTRAINT forum_thread_likes_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON UPDATE CASCADE ON DELETE CASCADE;


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
-- Name: forum_thread_replies forum_thread_replies_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_replies
    ADD CONSTRAINT forum_thread_replies_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON UPDATE CASCADE ON DELETE CASCADE;


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
-- Name: forum_thread_reply_tagged_cars forum_thread_reply_tagged_cars_car_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_reply_tagged_cars
    ADD CONSTRAINT forum_thread_reply_tagged_cars_car_id_fkey FOREIGN KEY (car_id) REFERENCES public.cars(id) ON DELETE CASCADE;


--
-- Name: forum_thread_reply_tagged_cars forum_thread_reply_tagged_cars_reply_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_reply_tagged_cars
    ADD CONSTRAINT forum_thread_reply_tagged_cars_reply_id_fkey FOREIGN KEY (reply_id) REFERENCES public.forum_thread_replies(id) ON DELETE CASCADE;


--
-- Name: forum_thread_reply_tagged_people forum_thread_reply_tagged_people_reply_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_reply_tagged_people
    ADD CONSTRAINT forum_thread_reply_tagged_people_reply_id_fkey FOREIGN KEY (reply_id) REFERENCES public.forum_thread_replies(id) ON DELETE CASCADE;


--
-- Name: forum_thread_reply_tagged_people forum_thread_reply_tagged_people_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_reply_tagged_people
    ADD CONSTRAINT forum_thread_reply_tagged_people_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


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
-- Name: forum_thread_tagged_cars forum_thread_tagged_cars_car_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_tagged_cars
    ADD CONSTRAINT forum_thread_tagged_cars_car_id_fkey FOREIGN KEY (car_id) REFERENCES public.cars(id) ON DELETE CASCADE;


--
-- Name: forum_thread_tagged_cars forum_thread_tagged_cars_thread_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_tagged_cars
    ADD CONSTRAINT forum_thread_tagged_cars_thread_id_fkey FOREIGN KEY (thread_id) REFERENCES public.forum_threads(id) ON DELETE CASCADE;


--
-- Name: forum_thread_tagged_people forum_thread_tagged_people_thread_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_tagged_people
    ADD CONSTRAINT forum_thread_tagged_people_thread_id_fkey FOREIGN KEY (thread_id) REFERENCES public.forum_threads(id) ON DELETE CASCADE;


--
-- Name: forum_thread_tagged_people forum_thread_tagged_people_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_tagged_people
    ADD CONSTRAINT forum_thread_tagged_people_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: forum_thread_topics forum_thread_topics_thread_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_topics
    ADD CONSTRAINT forum_thread_topics_thread_id_fkey FOREIGN KEY (thread_id) REFERENCES public.forum_threads(id) ON DELETE CASCADE;


--
-- Name: forum_thread_topics forum_thread_topics_topic_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forum_thread_topics
    ADD CONSTRAINT forum_thread_topics_topic_id_fkey FOREIGN KEY (topic_id) REFERENCES public.forum_thread_topic_options(id) ON DELETE RESTRICT;


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
    ADD CONSTRAINT forum_threads_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON UPDATE CASCADE ON DELETE CASCADE;


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
-- Name: reputation_score_history reputation_score_history_reason_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reputation_score_history
    ADD CONSTRAINT reputation_score_history_reason_fkey FOREIGN KEY (reason) REFERENCES public.reputation_score_reason_options(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: reputation_score_history reputation_score_history_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reputation_score_history
    ADD CONSTRAINT reputation_score_history_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON UPDATE CASCADE ON DELETE CASCADE;


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
-- Name: user_badges user_badges_badge_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_badges
    ADD CONSTRAINT user_badges_badge_id_fkey FOREIGN KEY (badge_id) REFERENCES public.badges(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: user_badges user_badges_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_badges
    ADD CONSTRAINT user_badges_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: user_devices_firebase_token user_devices_firebase_token_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_devices_firebase_token
    ADD CONSTRAINT user_devices_firebase_token_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: user_presence user_presence_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_presence
    ADD CONSTRAINT user_presence_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE;


--
-- Name: vehicle_entry_attachments vehicle_entry_attachments_entry_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicle_entry_attachments
    ADD CONSTRAINT vehicle_entry_attachments_entry_id_fkey FOREIGN KEY (entry_id) REFERENCES public.vehicle_history_entries(id) ON DELETE CASCADE;


--
-- Name: vehicle_history_entries vehicle_history_entries_business_profile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicle_history_entries
    ADD CONSTRAINT vehicle_history_entries_business_profile_id_fkey FOREIGN KEY (business_profile_id) REFERENCES public.profiles(id) ON DELETE SET NULL;


--
-- Name: vehicle_history_entries vehicle_history_entries_car_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicle_history_entries
    ADD CONSTRAINT vehicle_history_entries_car_id_fkey FOREIGN KEY (car_id) REFERENCES public.cars(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: vehicle_history_entries vehicle_history_entries_category_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicle_history_entries
    ADD CONSTRAINT vehicle_history_entries_category_fkey FOREIGN KEY (category) REFERENCES public.vehicle_entry_options(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: vehicle_history_entries vehicle_history_entries_mileage_unit_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicle_history_entries
    ADD CONSTRAINT vehicle_history_entries_mileage_unit_id_fkey FOREIGN KEY (mileage_unit_id) REFERENCES public.car_distance_units(id);


--
-- Name: vehicle_history_entries vehicle_history_entries_parent_entry_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicle_history_entries
    ADD CONSTRAINT vehicle_history_entries_parent_entry_id_fkey FOREIGN KEY (parent_entry_id) REFERENCES public.vehicle_history_entries(id) ON DELETE SET NULL;


--
-- Name: vehicle_history_entries vehicle_history_entries_price_currency_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicle_history_entries
    ADD CONSTRAINT vehicle_history_entries_price_currency_fkey FOREIGN KEY (price_currency) REFERENCES public.price_currencies_options(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: vehicle_history_entries vehicle_history_entries_status_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicle_history_entries
    ADD CONSTRAINT vehicle_history_entries_status_fkey FOREIGN KEY (status) REFERENCES public.vehicle_entry_status(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: admin_team_members; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: app_language_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: badges; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: blocked_accounts; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: business_account_active_status_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: business_account_verification_status_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: business_accounts; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: business_hours; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: business_type_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_brands; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_color_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_distance_units; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_drivetrain_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_event_approval_status_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_event_attendee_status; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_event_attendees_list; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_event_categories; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_event_organizer_rules; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_event_organizers; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_event_participant_status_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_event_participants; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_event_status_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_events; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_fuel_type_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_gallery; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_mod_categories; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_models; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_modification_gallery; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_modifications; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_share_links; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_status_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: cars; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: cities; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: comment_likes; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: comment_reports; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: comment_tagged_cars; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: comment_tagged_people; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: comments; Type: ROW SECURITY; Schema: public; Owner: -
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
-- Name: event_car_meet; Type: ROW SECURITY; Schema: public; Owner: -
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
-- Name: feedback_feed_feedback_types; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: feedback_feed_messages; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: feedback_feed_status_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: feedback_feed_votes; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: feedback_status_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: feedback_subscriptions; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: feedback_type_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: feedback_votes; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: follows; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: forum_post_likes; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: forum_shortcuts; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: forum_thread_likes; Type: ROW SECURITY; Schema: public; Owner: -
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
-- Name: forum_thread_reply_tagged_cars; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: forum_thread_reply_tagged_people; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: forum_thread_reports; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: forum_thread_saves; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: forum_thread_tagged_cars; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: forum_thread_tagged_people; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: forum_thread_topic_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: forum_thread_topics; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: forum_threads; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: garages; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: moderation_actions; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: moderation_cases; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: notification_preferences; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: notifications; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: post_images; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: post_likes; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: post_reports; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: post_shares; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: posts; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: price_currencies_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: profile_reports; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: profiles; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: profiles profiles_select_own; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: profiles profiles_update_own; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: report_reasons; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: reputation_score_history; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: reputation_score_reason_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: saved_posts; Type: ROW SECURITY; Schema: public; Owner: -
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
-- Name: tagged_people; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: user_badges; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: user_devices_firebase_token; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: user_presence; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: vehicle_entry_attachments; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: vehicle_entry_options; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: vehicle_entry_status; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: vehicle_history_entries; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_event_contest_categories car_event_contest_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_contest_categories
    ADD CONSTRAINT car_event_contest_categories_pkey PRIMARY KEY (id);


--
-- Name: car_event_contests car_event_contests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_contests
    ADD CONSTRAINT car_event_contests_pkey PRIMARY KEY (id);


--
-- Name: car_event_contest_entries car_event_contest_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_contest_entries
    ADD CONSTRAINT car_event_contest_entries_pkey PRIMARY KEY (contest_id, car_id);


--
-- Name: car_event_contest_votes car_event_contest_votes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_contest_votes
    ADD CONSTRAINT car_event_contest_votes_pkey PRIMARY KEY (contest_id, voter_id);


--
-- Name: car_event_contests_event_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX car_event_contests_event_status_idx ON public.car_event_contests USING btree (event_id, status);


--
-- Name: car_event_contest_entries_car_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX car_event_contest_entries_car_idx ON public.car_event_contest_entries USING btree (car_id);


--
-- Name: car_event_contest_entries_owner_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX car_event_contest_entries_owner_idx ON public.car_event_contest_entries USING btree (owner_id);


--
-- Name: car_event_contest_entries_board_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX car_event_contest_entries_board_idx ON public.car_event_contest_entries USING btree (contest_id, status, votes_count DESC);


--
-- Name: car_event_contest_entries_podium_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX car_event_contest_entries_podium_idx ON public.car_event_contest_entries USING btree (car_id) WHERE (final_rank <= 3);


--
-- Name: car_event_contest_votes_entry_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX car_event_contest_votes_entry_idx ON public.car_event_contest_votes USING btree (contest_id, car_id);


--
-- Name: car_event_participants_car_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX car_event_participants_car_id_idx ON public.car_event_participants USING btree (car_id);


--
-- Name: car_event_contest_votes trg_contest_vote_counts; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_contest_vote_counts AFTER INSERT OR DELETE OR UPDATE ON public.car_event_contest_votes FOR EACH ROW EXECUTE FUNCTION public.trg_contest_vote_counts();


--
-- Name: car_event_contest_entries trg_contest_entries_count; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_contest_entries_count AFTER INSERT OR DELETE OR UPDATE ON public.car_event_contest_entries FOR EACH ROW EXECUTE FUNCTION public.trg_contest_entries_count();


--
-- Name: car_event_contests car_event_contests_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_contests
    ADD CONSTRAINT car_event_contests_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.car_events(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: car_event_contests car_event_contests_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_contests
    ADD CONSTRAINT car_event_contests_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.car_event_contest_categories(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: car_event_contest_entries car_event_contest_entries_contest_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_contest_entries
    ADD CONSTRAINT car_event_contest_entries_contest_id_fkey FOREIGN KEY (contest_id) REFERENCES public.car_event_contests(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: car_event_contest_entries car_event_contest_entries_car_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_contest_entries
    ADD CONSTRAINT car_event_contest_entries_car_id_fkey FOREIGN KEY (car_id) REFERENCES public.cars(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: car_event_contest_entries car_event_contest_entries_owner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_contest_entries
    ADD CONSTRAINT car_event_contest_entries_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES public.profiles(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: car_event_contest_votes car_event_contest_votes_voter_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_contest_votes
    ADD CONSTRAINT car_event_contest_votes_voter_id_fkey FOREIGN KEY (voter_id) REFERENCES public.profiles(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: car_event_contest_votes car_event_contest_votes_entry_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.car_event_contest_votes
    ADD CONSTRAINT car_event_contest_votes_entry_fkey FOREIGN KEY (contest_id, car_id) REFERENCES public.car_event_contest_entries(contest_id, car_id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: car_event_contest_categories; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_event_contests; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_event_contest_entries; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: car_event_contest_votes; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- PostgreSQL database dump complete
--




--
-- Applied from supabase/migrations/20260911120000_participant_cards_on_posts.sql until the next
-- dump regeneration folds it in above.
--

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
