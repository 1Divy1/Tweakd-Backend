-- Reposts replace shares.
--
-- A repost is Instagram's, not Facebook's: one tap puts someone else's post in front of your
-- followers, with nothing of your own attached. So the optional note a share could carry
-- (post_shares.content) and the separate count it fed (posts.quote_shares_count) both go. The
-- table keeps its name — a row in post_shares *is* a repost.
--
-- Apply AFTER deploying the backend build that no longer maps post_shares.content or
-- posts.quote_shares_count: Hibernate validates only the columns an entity maps, so the new build
-- runs against the old schema, but the old build crashes against the new one.

begin;

-- 1. The ranking trigger lists quote_shares_count in its UPDATE OF clause, which pins the column.
drop trigger if exists trg_posts_ranking_score on public.posts;

-- 2. One weight for every repost. Plain shares already weighed 6; a note no longer exists to earn 10.
create or replace function public.compute_post_ranking_score() returns trigger
    language plpgsql
    set search_path to ''
    as $$
declare
  w_like    constant double precision := 1;
  w_comment constant double precision := 4;
  w_repost  constant double precision := 6;
  w_save    constant double precision := 3;
  s         double precision;
begin
  s := w_like    * coalesce(new.likes_count, 0)
     + w_comment * coalesce(new.comments_count, 0)
     + w_repost  * coalesce(new.shares_count, 0)
     + w_save    * coalesce(new.saved_count, 0);

  new.ranking_score :=
        log(greatest(s, 1))
      + extract(epoch from coalesce(new.created_at, now())) / 45000.0;

  return new;
end;
$$;

create or replace function public.sync_post_shares_count() returns trigger
    language plpgsql
    set search_path to ''
    as $$
begin
  if (tg_op = 'INSERT') then
    update public.posts set shares_count = shares_count + 1 where id = new.post_id;
  elsif (tg_op = 'DELETE') then
    update public.posts set shares_count = shares_count - 1 where id = old.post_id;
  end if;
  return null;
end;
$$;

-- 3. Reposting your own post is not a thing; drop any self-shares before counting.
delete from public.post_shares ps
 using public.posts p
 where p.id = ps.post_id
   and p.user_id = ps.user_id;

-- 4. Every surviving share, noted or not, is now a plain repost. Recount from the rows rather than
--    folding the two counters, so the self-shares removed above (deleted while the trigger still
--    split by content) cannot leave a counter off by one.
update public.posts p
   set shares_count = coalesce(c.n, 0)
  from (select p2.id, count(ps.post_id) as n
          from public.posts p2
          left join public.post_shares ps on ps.post_id = p2.id
         group by p2.id) c
 where c.id = p.id;

alter table public.posts drop column quote_shares_count;
alter table public.post_shares drop column content;

-- 5. A repost's time is what the feed ranks it by and what the profile's Reposts tab pages on.
update public.post_shares set created_at = now() where created_at is null;
alter table public.post_shares alter column created_at set not null;

-- 6. Restore the ranking trigger without the dropped column, then rescore every post: the recount
--    in step 4 ran while no trigger was attached.
create trigger trg_posts_ranking_score
    before insert or update of likes_count, comments_count, shares_count, saved_count
    on public.posts
    for each row execute function public.compute_post_ranking_score();

update public.posts set shares_count = shares_count;

comment on table public.post_shares is
    'Reposts: one row per (post, user) who reposted it to their followers. Never the author''s own post.';
comment on column public.posts.shares_count is
    'How many users reposted this post. Maintained by trg_sync_post_shares_count.';

commit;
