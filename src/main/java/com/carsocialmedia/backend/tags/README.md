# tags module

Owns the **"tags" section of a profile** — the third tab next to *posts* and *garage*: everywhere a
user or one of their cars has been tagged, merged into one chronological feed.

Like `feed`, this module holds **no data of its own**. The tag rows live with the content that
carries them (`posts` owns post and comment tags, `forums` owns thread and reply tags); this module
composes those two plus `garage` (the profile owner's car ids) and `profile` (username → id).

Private DMs can carry car tags too, but they are deliberately **out of scope** — a DM is not
something a profile visitor should be able to browse.

## The four surfaces

| `kind` | Tagged content | Join tables | Extra field |
|---|---|---|---|
| `post` | a classic post | `tagged_people` / `tagged_cars` | — |
| `post_comment` | a comment (or threaded reply) on a post | `comment_tagged_people` / `comment_tagged_cars` | `post` — the comment's post |
| `forum_thread` | a forum thread's OP | `forum_thread_tagged_people` / `forum_thread_tagged_cars` | — |
| `forum_reply` | a reply inside a thread | `forum_thread_reply_tagged_people` / `forum_thread_reply_tagged_cars` | `thread` — the reply's thread |

Each item reuses the DTO the app already renders that content with (`PostDto`, `CommentDto`,
`ThreadCardDto`, `ReplyDto`), so the client can reuse its existing cards and deep-link straight into
the content. Unset slots are omitted from the JSON (`@JsonInclude(NON_NULL)`), so a post item is
just `{kind, tagged_at, target_id, post}`.

```json
{
  "items": [
    { "kind": "forum_reply", "tagged_at": "…", "target_id": "…", "thread": { … }, "reply": { … } },
    { "kind": "post_comment", "tagged_at": "…", "target_id": "…", "post": { … }, "comment": { … } },
    { "kind": "post", "tagged_at": "…", "target_id": "…", "post": { … } }
  ],
  "next_cursor": "…"
}
```

## What the feed contains

- **Person tags and car tags, collapsed.** Being tagged alongside your own car is one item, not two.
- **Not your own content.** Filtered out in SQL, on the content's author. A car tag can only exist
  without its owner being tagged as a person when the author *is* the owner, so this also removes
  the "I tagged my own car in my own post" rows — they already live under the posts tab.
- **Not soft-deleted comments or replies.** Those render as "[deleted]" and drop their tags anyway.
  Anonymized threads *do* stay: they keep their title, body and replies, only the author is hidden.

## Ordering and paging

Ordered by **when the tag was made**, not when the content was written — a tag added today on an old
post surfaces at the top. The sort key is `(tagged_at, id)` descending, with `id` compared the way
Postgres compares `uuid` (unsigned, byte-wise) so the SQL keyset and the in-memory merge agree.

Paging is a single opaque keyset cursor over the merged feed. Each of the four streams re-applies
that same cursor independently and returns at most `size + 1` refs; the refs are merged, sorted and
trimmed, and only then is content hydrated — so a page never assembles DTOs it will not return.
Hydration costs one batch call per content type (a comment's parent post rides along in the post
batch; a reply's parent thread in the thread batch).

`tagged_at` falls back to the content's creation time for rows whose tag timestamp is null — only
possible on `tagged_people` / `tagged_cars`, whose `created_at` predates the NOT NULL used by the
newer tables.

**A page can come back shorter than `size` even when more items exist** (an item tagged on two
surfaces of the merge collapses after the fetch). `next_cursor == null` is the only end-of-feed
signal.

## Untagging yourself

`DELETE /api/v1/tags/{kind}/{targetId}` removes the caller's tags from that content. The tag is
**deleted, not hidden**: the content stops listing the caller among its tagged people for everyone,
and the item leaves their tags section for every viewer.

Any of the caller's **own cars** tagged on the same content go with it — leaving them would break
the invariant the tagging endpoints enforce ("a tagged car's owner is tagged too"). Other people's
tags, including their cars, are untouched, and the author is free to tag the caller again.
Idempotent: untagging content you are not tagged in is a no-op, not an error.

## Public API — `TagsService`

| Method | REST | Notes |
|--------|------|-------|
| `getTaggedContent(currentUserId, username, cursor, size)` | `GET /api/v1/tags/by-username/{username}?cursor=&size=` | A profile's tags section. All accounts are public, so any viewer may read any profile's |
| `getMyTaggedContent(currentUserId, cursor, size)` | `GET /api/v1/tags/me?cursor=&size=` | The caller's own, without the username round-trip |
| `untagSelf(currentUserId, kind, targetId)` | `DELETE /api/v1/tags/{kind}/{targetId}` | 204; 400 on an unknown kind, 404 if the content is gone |

Per-item viewer state (`viewer_has_liked`, `viewer_has_saved`) is the **viewer's**, not the profile
owner's — the DTOs are hydrated with the caller's id.

## Dependencies

`posts` (post + comment tags and DTOs), `forums` (thread + reply tags and DTOs), `garage`
(`findCarIdsByOwner`), `profile` (`findIdByUsername`). No module depends on `tags`.

No tables, no migrations: every reverse-lookup index this module's queries need
(`idx_*_tagged_people_user_id`, `idx_*_tagged_cars_car_id`) already existed.
