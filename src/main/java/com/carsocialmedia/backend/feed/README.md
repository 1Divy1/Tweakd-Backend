# feed module

Owns the **feed page**: the policy for *which* posts to show and *in what order*. It holds no
post data of its own — it composes the `posts` module for the post rows and their assembly into
`PostDto`s.

The app has two feeds:

- **Global feed** — the most viral posts across the whole app. *Implemented.*
- **Personalized feed** — posts from accounts the user follows, tuned by their preferences.
  *Planned* (will compose `posts` + `follow` + profile preferences here).

## Global feed

A post's virality is captured by `posts.ranking_score` (Postgres `double precision`), maintained by
the Supabase trigger `compute_post_ranking_score`. The formula is a time-decayed engagement score:

```
ranking_score = log10(max(weighted_engagement, 1)) + epoch(created_at) / 45000
weighted_engagement = 1·likes + 4·comments + 6·plain_shares + 10·quote_shares + 3·saves
```

The time term means a brand-new post starts above older zero-engagement posts, and engagement then
lifts it further — so "viral" naturally blends freshness and interaction. App code never writes the
score; it is read-only (`@Column(updatable = false, insertable = false)` on `PostEntity`).

The feed delegates to `PostsService.getRankedPosts`, which keyset-paginates over
`ranking_score DESC, id DESC` (index `idx_posts_ranking_keyset`) and reuses the posts module's batch
`PostDto` assembler. Because `ranking_score` is mutable, the feed is only *eventually consistent*
across pages — a post may occasionally repeat or be skipped as scores shift between requests, an
accepted trade-off for a ranked feed.

There is no privacy gate: every account in the app is public.

## Public API — `FeedService`

| Method | REST | Notes |
|--------|------|-------|
| `getGlobalFeed(currentUserId, cursor, size)` | `GET /api/v1/feed/global?cursor=&size=` | Keyset page of `PostPageDto`, most viral first. `cursor` omitted = first page; echo `nextCursor` back to page on. |

Depends on the `posts` module (ranked post data + `PostDto` assembly).
