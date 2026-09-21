# posts module

Owns everything in the feed: `posts` and their child tables `post_images`,
`tagged_people`, `tagged_cars`, plus engagement tables `post_likes`, `post_shares` (reposts),
`saved_posts`, `comments`, `comment_likes`, and the comment tag tables
`comment_tagged_people` / `comment_tagged_cars`.

Tagged people and tagged cars are stored as flat reference UUIDs — the post never holds
profile or car entities. They are resolved to `ProfileSearchResultDto` / `CarSummaryDto`
through the `profile` and `garage` module interfaces when a `PostDto` is assembled.

**Tagging rule.** Tagging people and tagging cars are both optional, but a car can only be
tagged if its owner is tagged in the same post (the author's own cars are exempt — no
self-tag needed). This matches the client flow: you search a user, then pick from their
cars in a dropdown. On create and update the rule is enforced over the post's *resulting*
tag set — e.g. untagging a person whose car is still tagged is rejected
(`CarOwnerNotTaggedException`). Car ownership is resolved via `GarageService.findCarOwnerIds`.

The same rule applies to **comments**: `POST /{postId}/comments` accepts optional `tagged_people` /
`tagged_cars` (≤30 each, deduplicated server-side), validated against the comment's author. Comments
have no edit endpoint, so a comment's tag set is fixed at creation. All three comment read paths
(the create response, the comment page and the reply page) echo the tags back as
`ProfileSearchResultDto` / `CarSummaryDto`; a soft-deleted comment returns empty tag lists, exactly
like a deleted forum reply (the join rows stay in place, they're just not looked up).

A tagged user can **untag themselves** from a post or a comment
(`removeSelfTagsFromPost` / `removeSelfTagsFromComment`, exposed by the `tags` module's
`DELETE /api/v1/tags/{kind}/{targetId}`). The tag row is deleted, not hidden, and any of that
user's *own* cars tagged on the same content go with it — otherwise the removal would leave a
tagged car whose owner is no longer tagged. Other people's tags are untouched.

The `tags` module also reads these tables in reverse ("what is this user tagged in?") through
`findTaggedPostRefs` / `findTaggedCommentRefs` — keyset streams of ids that exclude the tagged
user's own content and, for comments, soft-deleted rows. See the
[`tags` module](../tags/README.md).

Depends on the `profile` module (author + tagged-people resolution), the `garage` module
(tagged-car resolution + `findCarIdsByOwner` for untagging), and the `storage` module (presigned
upload URLs + key→URL).

## Create-a-post flow

Mirrors the garage "add a car" flow — the post row is created first so its id exists, then
images are uploaded to R2 and their keys sent back. No file bytes ever travel through the
backend.

1. `POST /api/v1/posts` — create the post (caption, tagged people/cars, count toggles).
   Returns the `PostDto` (no images yet) carrying the new `id`.
2. `POST /api/storage/posts/{postId}/upload-urls` with `{ "count": N }` — returns `N`
   `{ key, uploadUrl }` slots in one call (no per-image round-trips).
3. Flutter `PUT`s each image straight to R2 at `uploadUrl`.
4. `PATCH /api/v1/posts/{postId}/images` — send the ordered `keys`; the backend replaces
   the post's `post_images` rows (position = list index) and deletes any dropped key from R2
   after the transaction commits.

Image keys are namespaced `posts/{postId}/{uuid}.webp` in the `POSTS` R2 bucket. Only the
key is persisted; the public URL is built on read via `StorageService.publicUrl`.

Step 2 is author-only (`PostUploadAccessPolicy`), and step 4 rejects any key that is neither already
on the post nor under `posts/{postId}/` — otherwise a post could claim another post's image and
delete it on its next edit.

## Public API — `PostsService`

| Method | Description |
|---|---|
| `createPost(currentUserId, CreatePostRequest)` | Creates a post + its tagged people/cars; returns the `PostDto` (no images) |
| `getPost(currentUserId, postId)` | One fully-assembled post; privacy-gated |
| `getMyPosts(currentUserId, cursor, size)` | Keyset page of the caller's own posts, newest first |
| `getUserPosts(currentUserId, username, cursor, size)` | Keyset page of another user's posts, newest first; privacy-gated |
| `updatePost(currentUserId, postId, UpdatePostRequest)` | Partial update (caption, tags, count toggles); owner only |
| `deletePost(currentUserId, postId)` | Deletes a post; child rows cascade, R2 images cleaned up after commit; owner only |
| `savePostImageKeys(currentUserId, postId, keys)` | Replace-all of a post's images by R2 key, in order; owner only |
| `getComments(viewerId, postId, cursor, size)` | One keyset page of root comments, newest first |
| `getReplies(viewerId, postId, commentId, cursor, size)` | One keyset page of a comment's replies, newest first |
| `getSavedPosts(currentUserId, cursor, size)` | Keyset page of the caller's saved posts, newest save first |
| `getSharedPosts(currentUserId, cursor, size)` | Keyset page of the caller's reposts, newest repost first |
| `getUserReposts(currentUserId, username, cursor, size)` | Keyset page of a user's reposts, newest repost first (profile Reposts tab) |
| `getRankedPosts(currentUserId, followeeIds, cursor, size)` | Global feed page; followee reposts rank as fresh as the repost and carry `reposted_by` — see [Reposts](#reposts) |
| `getPostLikers(postId, cursor, size)` | One keyset page of likers, most recent first |
| `likePost / unlikePost(currentUserId, postId)` | Like / unlike a post (idempotent, `ON CONFLICT DO NOTHING`). The author is notified once per (post, liker), ever — the `post_like_notifications` ledger outlives an unlike (migration `20260915140000_like_notify_once.sql`) |
| `savePost / unsavePost(currentUserId, postId)` | Save / unsave (bookmark) a post (idempotent) |
| `sharePost(currentUserId, postId)` / `unsharePost(currentUserId, postId)` | Repost / undo (idempotent); reposting your own post → `CannotRepostOwnPostException` |
| `shareModification(currentUserId, ShareModificationRequest)` | Shares one of the caller's build-log mods as an ordinary post that tags the car and carries the mod's id. **Idempotent** — an already-shared mod returns its existing post |
| `addComment(currentUserId, postId, CreateCommentRequest)` | Add a comment or threaded reply (+ its tagged people/cars); returns the `CommentDto` |
| `deleteComment(currentUserId, postId, commentId)` | Soft-delete a comment (idempotent); comment author or post owner |
| `likeComment / unlikeComment(currentUserId, postId, commentId)` | Like / unlike a comment (idempotent) |
| `findTaggedPostRefs` / `findTaggedCommentRefs(userId, ownedCarIds, cursorTaggedAt, cursorId, limit)` | Keyset stream of "content this user (or their car) is tagged in", newest tag first; excludes their own content. Consumed by `tags` |
| `getPostsByIds(viewerId, postIds)` / `getCommentsByIds(viewerId, commentIds)` | Batch DTO assembly in the requested order; unresolvable ids are dropped |
| `removeSelfTagsFromPost / removeSelfTagsFromComment(userId, targetId)` | Untag yourself: person tag + your own cars' tags on that content (idempotent) |

**Engagement counts are trigger-owned.** Every denormalized count — `posts.likes_count`,
`saved_count`, `shares_count` (reposts), `comments_count`, and `comments.likes_count`
— is maintained by a Supabase `AFTER INSERT/DELETE` (and, for comments, `UPDATE`) trigger on the
engagement table. The service only inserts/deletes the engagement row and **never writes a count
column**. The comments trigger decrements `comments_count` when
a comment flips to `is_deleted = true`, so deletion is a soft-delete (an `UPDATE`), not a row removal.

### DTOs / records

| Type | Used for |
|---|---|
| `PostDto` | Full post for feed/detail: author, images, tagged people/cars, counts + visibility flags, viewer like/save/repost state, `participantCard` / `modShareCard` (each derived on read, drawn in place of images), `repostedBy` (feed only) |
| `PostImageDto` | One image: `id`, full `imageUrl` (built from R2 key), `displayOrder` |
| `CreateCommentRequest` | Comment payload: `content` (required) + optional `parentCommentId` for a reply, `taggedPeople`, `taggedCars` (≤30 each) |
| `RepostedByDto` | `users` (≤2 followed reposters, most recent first) + `totalCount`; set on global-feed posts only |
| `ShareModificationRequest` | Names the build-log mod to share: `modification_id`. No caption — the share is a toggle in the "log a mod" flow, and the mod's own description is what the card shows |
| `CreatePostRequest` | Create payload: caption, `taggedPeople`, `taggedCars`, three `*CountEnabled` toggles. No images |
| `UpdatePostRequest` | Partial-update payload (PATCH semantics: null = leave unchanged; non-null tag list = replace-all) |
| `PostImageKeysRequest` | Ordered list of R2 keys (max 10) — the post's complete desired image set |
| `PostPageDto` | One keyset page of posts (`items` + `nextCursor`) — profile grids |
| `CommentDto` / `CommentPageDto` | A comment (incl. `taggedPeople` / `taggedCars`) / one keyset page of comments |
| `LikerPageDto` | One keyset page of likers (`ProfileSearchResultDto` items) |

### Exceptions

| Exception | HTTP | Trigger |
|---|---|---|
| `PostNotFoundException` | 404 | Post id doesn't exist |
| `CommentNotFoundException` | 404 | Comment id doesn't exist (or doesn't belong to the post in the URL) |
| `NotPostOwnerException` | 403 | Caller tries to mutate a post they don't own |
| `NotCommentOwnerException` | 403 | Caller tries to delete a comment they neither authored nor own the post of |
| `InvalidReferenceException` | 400 | A tagged person or car id doesn't exist (post **or** comment tags) |
| `CarOwnerNotTaggedException` | 400 | A tagged car's owner is neither the author nor a tagged person (post **or** comment tags) |
| `ModificationNotFoundException` | 404 | Sharing a mod that does not exist or sits on a car the caller does not own. One 404 for both, so another user's build log cannot be probed by id |
| `ParticipantCardNotFoundException` | 404 | Sharing a participant card that does not exist or is not the caller's (event not marked finished, car not an accepted participant, someone else's) |
| `ParticipantCardCooldownException` | 409 | The same card was shared within `PARTICIPANT_CARD_REPOST_COOLDOWN` (48 h). Body carries `error = participant_card_cooldown` and `details.next_post_allowed_at` (ISO-8601) |
| `CannotRepostOwnPostException` | 400 | The caller tries to repost their own post |
| `InvalidCursorException` | 400 | A pagination cursor can't be decoded |

## REST endpoints

Base path: `/api/v1/posts`

| Method | Path | Description |
|---|---|---|
| POST | `/` | Create a post (201) |
| GET | `/me` | Keyset page of the caller's own posts (`?cursor=&size=`) |
| GET | `/saved` | Keyset page of the caller's saved posts (`?cursor=&size=`) |
| GET | `/shared` | Keyset page of the caller's reposts (`?cursor=&size=`) |
| GET | `/by-username/{username}/reposts` | Keyset page of a user's reposts, newest repost first (`?cursor=&size=`) |
| GET | `/by-username/{username}` | Keyset page of another user's posts; privacy-gated (`?cursor=&size=`) |
| GET | `/{postId}` | One post, fully assembled; privacy-gated |
| PATCH | `/{postId}` | Partial update of caption / tags / count toggles (owner only) |
| DELETE | `/{postId}` | Delete a post (204; owner only) |
| PATCH | `/{postId}/images` | Replace the post's images by ordered R2 keys |
| GET | `/{postId}/comments` | Keyset page of root comments (`?cursor=&size=`) |
| GET | `/{postId}/comments/{commentId}/replies` | Keyset page of a comment's replies (`?cursor=&size=`) |
| GET | `/{postId}/likes` | Keyset page of likers (`?cursor=&size=`) |
| POST / DELETE | `/{postId}/likes` | Like / unlike a post (204; idempotent) |
| POST / DELETE | `/{postId}/saves` | Save / unsave a post (204; idempotent) |
| POST / DELETE | `/{postId}/shares` | Repost / undo (204; idempotent). No body — one sent by an older client is ignored. 400 on your own post |
| POST | `/mod-share` | Share one of the caller's build-log modifications (201 → `PostDto`). Body `{ modification_id }`. 404 if it is not on one of their cars. Idempotent: an already-shared mod returns its first post |
| POST | `/participant-card` | Share one of the caller's participant cards (201 → `PostDto`). Body `{ event_id, car_id, description? }`. 404 if it is not their card; 409 `participant_card_cooldown` inside the repost cooldown |
| POST | `/{postId}/comments` | Add a comment / reply (201); body `{ "content": "…", "parent_comment_id": "…"?, "tagged_people": [uuid]?, "tagged_cars": [uuid]? }` |
| DELETE | `/{postId}/comments/{commentId}` | Soft-delete the caller's comment (204; author only) |
| POST / DELETE | `/{postId}/comments/{commentId}/likes` | Like / unlike a comment (204; idempotent) |

> Image upload URLs are issued in batch by the storage module:
> `POST /api/storage/posts/{postId}/upload-urls` with `{ "count": N }`.

The profile grid uses a **batch assembler** (`PostsServiceImpl.toPostDtos`) that resolves a whole
page with a fixed, small number of queries (one `profileService.findByIds` for all authors +
tagged people, one `garageService.findCarsByIds`, one batch each for images / tags / viewer
like-save) instead of per-row lookups. The single-post `toPostDto` delegates to it.

A global feed endpoint is still pending, but it now only needs its own keyset query
(`posts ORDER BY created_at DESC, id DESC`, no `user_id` filter) plus a `getFeed` that reuses
`toPostDtos` — the assembly work is already done.

## Entities

| Entity → table | Notes |
|---|---|
| `PostEntity` → `posts` | App-generated `id`; `createdAt` DB-managed (`insertable=false`); `updatedAt` set on write; counts default 0, set explicitly on create |
| `PostImageEntity` → `post_images` | `imageKey` holds the R2 key only; `displayOrder` is `smallint` |
| `TaggedPersonEntity` → `tagged_people` | Composite PK `(post_id, user_id)`; `user_id` is a flat `profiles.id` ref |
| `TaggedCarEntity` → `tagged_cars` | Composite PK `(post_id, car_id)`; `car_id` is a flat `cars.id` ref |
| `PostLikeEntity` / `SavedPostEntity` / `PostShareEntity` | Composite-PK engagement rows |
| `CommentEntity` / `CommentLikeEntity` | Threaded comments (self-ref `parent_comment_id`) and their likes |
| `CommentTaggedPersonEntity` → `comment_tagged_people` | Composite PK `(comment_id, user_id)`; flat `profiles.id` ref |
| `CommentTaggedCarEntity` → `comment_tagged_cars` | Composite PK `(comment_id, car_id)`; flat `cars.id` ref |

## Domain events published

For in-app notifications, the service publishes small Spring events (in the posts public API root
package) at the point an engagement is known to have happened, inside the `@Transactional` method
(so an after-commit listener never fires on a rollback). Events carry ids only — no DTOs/entities.
The **self-notify skip** (actor == post author) lives at the publish site, so consumers never see a
self-event. The `notification` module consumes these; nothing depends back on posts.

| Event | Published by | When | Recipient |
|---|---|---|---|
| `PostLikedEvent(postId, recipientId, actorId)` | `likePost` | only on a real first like (not on a duplicate) | post author |
| `PostCommentedEvent(postId, commentId, recipientId, actorId, excerpt)` | `addComment` | any comment or reply, whatever the nesting | post author |
| `PostSharedEvent(postId, recipientId, actorId)` | `sharePost` | only on a real first repost | post author |
| `PostTaggedEvent(postId, recipientId, actorId, carTagged)` | `createPost` / `updatePost` | one per **newly** tagged user (re-saving an unchanged set is silent) | the tagged user |
| `PostCommentTaggedEvent(postId, commentId, recipientId, actorId, carTagged)` | `addComment` | one per tagged user (comments aren't editable, so every tag is new) | the tagged user |

Note: `likePost` / `sharePost` / `addComment` now load the post row (for the author id) instead of a
bare existence check.

A tagged car's owner is tagged as a person too unless the car is the author's own, so the two
collapse into one event per recipient carrying `carTagged`. A user tagged in a comment on their own
post receives both a `PostCommentedEvent` and a `PostCommentTaggedEvent`.

## Cross-module dependencies

- **`profile.ProfileService.findByIds`** — resolve the author and tagged people.
- **`garage.GarageService.findCarsByIds`** — resolve tagged cars (no privacy gating; built for this).
- **`garage.GarageService.findCarOwnerIds`** — resolve each tagged car's owner to enforce the tagging rule.
- **`mapevents.MapEventContestsService.findParticipantCards` / `findOwnedParticipantCard`** — derive the participant cards posts share (one batch per feed page) and check ownership before sharing one. The only `posts → mapevents` edge; `mapevents` does not depend on `posts`.
- **`storage.StorageService`** — `postImageUploadUrlRequest`, `publicUrl(POSTS, key)`, and
  `deleteByKeys(POSTS, …)`. The posts module owns key persistence; storage owns R2 I/O.

## Moderation (admin module)

`getPostModerationSnapshot` / `getCommentModerationSnapshot` return the uniform
`shared.moderation.ModerationContentDto` (author, text, resolved image URLs);
`deletePostAsModerator` / `deleteCommentAsModerator` bypass the ownership check but keep the
delete semantics (post: hard delete + R2 cleanup after commit; comment: soft delete). The admin
module snapshots content into `moderation_actions` **before** calling the hard delete, since
report rows CASCADE away with the post. No auth here — the admin module gates these.

## Participant cards on posts

A post may share a **participant card** (see the `mapevents` README). The post stores only the
card's pair — `posts.participant_card_event_id` / `participant_card_car_id`, both or neither, a
composite FK onto `car_event_participants` with `ON DELETE SET NULL` — and `toPostDtos` re-derives
every card on the page in **one** `findParticipantCards` call, so `PostDto.participant_card` always
shows the real placements. A pair that no longer derives (car deleted, entry revoked) comes back
`null` and the post renders as a plain one.

`shareParticipantCard` creates an ordinary post through the same path as `createPost`: it tags the
car (so the post also appears among the car's tagged posts) and sets the card reference. The request
only *names* the card; ownership is checked through `mapevents`, and nothing on the card is taken
from the client. Reposting is allowed, but not the same card within
`PARTICIPANT_CARD_REPOST_COOLDOWN` (48 h, measured from that card's most recent surviving post).
A transaction-scoped advisory lock on the pair (`lockParticipantCard`) serialises concurrent shares,
so a double tap cannot slip two posts past the check.

## Shared modifications

The same contract as a participant card, for a build-log mod: `posts.mod_share_modification_id` holds
only the modification's id (FK onto `car_modifications` with `ON DELETE SET NULL`), and `toPostDtos`
re-derives every card on the page in **one** `findModShareCards` call, so `PostDto.mod_share_card`
always shows the mod as it is now. A mod that no longer derives (deleted mod, deleted car) comes back
`null` and the post renders as a plain one, keeping its likes and comments.

The build log asks the question in reverse — "is this mod already in the feed?" —
through `garage.ModSharePostsProvider`, implemented here by `ModSharePostsAdapter`. Garage cannot
depend on posts (posts depends on garage), so garage declares the interface and this module fills
it, the same inversion as `PublicCarEventsProvider`. It is what puts an honest share row on a mod:
an offer when it is unshared, a link to the post when it is not.

`shareModification` goes through the same path as `createPost`: it tags the car and sets the
reference. The request only *names* the mod; ownership — and the car id — come from
`GarageService.findOwnedModificationCarId`, so nothing on the card is taken from the client, the
price included.

Where a participant card is rate-limited, a mod is **unique**: a mod is a one-time event, so it gets
one post. The service returns the post it finds (a double tap is a no-op) and the partial unique
index `posts_mod_share_modification_uq` is the backstop under concurrency, with the same advisory
lock (`lockParticipantCard("mod:" + id)`) serialising the two.

`ErrorResponse` now carries optional `error` (a stable code) and `details` (structured data) fields,
filled from `ApiException.getErrorCode()` / `getDetails()`; both are `null` for every other error.

## Reposts

A repost is Instagram's: one tap puts someone else's post in front of your followers, with nothing of
your own attached. The storage and wire still say `share` (`post_shares`, `/shares`, `shares_count`,
`shares_count_enabled`) — never rename them. The cross-repo reference, including how other content
types become repostable, is `REPOSTS.md` in the mobile repo.

- **Rules.** Idempotent by PK `(post_id, user_id)`, inserted with `ON CONFLICT DO NOTHING`
  (`PostShareRepository.insertIgnoringConflict`, race-safe on double taps); your own post is rejected
  (`CannotRepostOwnPostException`).
- **Notify once, ever.** `PostSharedEvent` is published once per (post, reposter). An undo deletes the
  `post_shares` row, so that table can't tell a first repost from repost → undo → repost; the
  `post_repost_notifications` ledger (`markRepostNotified`, returns 1 only the first time) outlives the
  repost and decides. Migration `20260915120000_post_repost_notify_once.sql`, tested by
  `PostRepostNotificationLedgerIT`.
- **Viewer flag.** `toPostDtos` adds `viewerHasReposted` with one `findRepostedPostIds` per page.
- **Feed boost.** `getRankedPosts` receives the viewer's followee ids from the `feed` module (posts never
  reads `follows`). `PostRepository.findRankedPostPage` ranks a post any followee reposted at
  `ranking_score + (epoch(latest followee repost) − epoch(created_at)) / 45000` — the engagement it earned
  plus the freshness of the repost, weights left in the trigger. Plain posts page off the ranking index,
  followee reposts are a separate bounded branch, the two are merged by `(score, id)` and the `RankCursor`
  carries that score. Followee ids bind as one Postgres array literal. The page's `reposted_by` comes from
  `findFollowedReposts` and its reposters join the page's single profile lookup.
- **Profile tab.** `getUserReposts` pages `post_shares` by `created_at` (`findSharedPage`).
- **Tests.** `PostRankedFeedRepositoryIT` exercises the query against the real triggers.
