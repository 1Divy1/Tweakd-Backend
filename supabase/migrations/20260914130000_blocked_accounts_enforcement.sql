-- Blocked accounts: enforce the block on the Supabase-side DM path.
--
-- Spring enforces blocks on every REST read (profile, search, posts, forums, DM lists, events),
-- but DMs are *sent* through the SECURITY DEFINER RPC dm_send_message and delivered over Realtime
-- Broadcast authorised by dm_topic_is_peer — both bypass Spring, so both check the table here.
--
-- A block is two-way: whoever placed it, neither account can message the other or join the
-- other's DM topic. A send to a blocked pair fails with exactly the error of an unknown recipient,
-- so the blocked account cannot tell a block from a deleted account.

-- Reverse lookups ("who blocked me") — the PK only covers blocker_id-first.
create index if not exists blocked_accounts_blocked_id_idx
    on public.blocked_accounts (blocked_id);

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'blocked_accounts_no_self_block'
  ) then
    alter table public.blocked_accounts
      add constraint blocked_accounts_no_self_block check (blocker_id <> blocked_id);
  end if;
end $$;


create or replace function public.dm_send_message(p_recipient_id uuid, p_content text default ''::text, p_tagged_car_ids uuid[] default '{}'::uuid[]) returns jsonb
    language plpgsql security definer
    set search_path to ''
    as $$
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

  -- A block in either direction reads as an unknown recipient: the blocked side must not learn
  -- that a block exists.
  if exists (
    select 1 from public.blocked_accounts b
     where (b.blocker_id = v_sender and b.blocked_id = p_recipient_id)
        or (b.blocker_id = p_recipient_id and b.blocked_id = v_sender)
  ) then
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


-- Realtime topic auth: a blocked pair no longer counts as peers, so neither side receives the
-- other's live messages, typing, or presence on the DM topic.
create or replace function public.dm_topic_is_peer(topic text) returns boolean
    language sql stable security definer
    set search_path to ''
    as $$
  select exists (
    select 1
    from public.dm_conversations c
    where ((c.user_a = auth.uid()
            and c.user_b::text = substring(topic from 6))
        or (c.user_b = auth.uid()
            and c.user_a::text = substring(topic from 6)))
      and not exists (
        select 1 from public.blocked_accounts b
         where (b.blocker_id = c.user_a and b.blocked_id = c.user_b)
            or (b.blocker_id = c.user_b and b.blocked_id = c.user_a)
      )
  );
$$;
