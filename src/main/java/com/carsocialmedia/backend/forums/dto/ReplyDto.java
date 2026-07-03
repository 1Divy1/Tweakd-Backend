package com.carsocialmedia.backend.forums.dto;

import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;

import java.time.Instant;
import java.util.UUID;

/**
 * A reply in a thread's reply tree. Replies load level by level: a keyset page of top-level
 * replies via {@code GET /threads/{id}/replies}, then each reply's direct children on demand via
 * {@code GET /posts/{id}/replies} (also keyset-paginated, oldest first). {@code replyCount} is the
 * number of direct children, so the client knows whether to offer "view replies". A soft-deleted
 * reply is kept as a placeholder while it still anchors children: {@code deleted == true} and
 * {@code author} / {@code content} are {@code null} (the client renders "[deleted]").
 *
 * @param id the reply id
 * @param author the reply author, or {@code null} when the reply is deleted
 * @param content the reply text, or {@code null} when the reply is deleted
 * @param likesCount how many users liked this reply
 * @param replyCount the number of direct children (fetched via {@code GET /posts/{id}/replies})
 * @param deleted whether the reply is soft-deleted
 * @param viewerHasLiked whether the requesting user liked this reply
 * @param createdAt when the reply was created
 */
public record ReplyDto(
        UUID id,
        ProfileSearchResultDto author,
        String content,
        int likesCount,
        int replyCount,
        boolean deleted,
        boolean viewerHasLiked,
        Instant createdAt
) {}
