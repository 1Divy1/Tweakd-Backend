package com.carsocialmedia.backend.forums.dto;

import com.carsocialmedia.backend.garage.dto.CarSummaryDto;
import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A reply in a thread's reply tree. Replies load level by level: a keyset page of top-level
 * replies via {@code GET /threads/{id}/replies}, then each reply's direct children on demand via
 * {@code GET /replies/{id}/replies} (also keyset-paginated, oldest first). {@code replyCount} is the
 * number of direct children, so the client knows whether to offer "view replies". A soft-deleted
 * reply is kept as a placeholder while it still anchors children: {@code deleted == true} and
 * {@code author} / {@code content} are {@code null} (the client renders "[deleted]").
 *
 * @param id the reply id
 * @param author the reply author, or {@code null} when the reply is deleted
 * @param content the reply text, or {@code null} when the reply is deleted
 * @param taggedPeople the profiles tagged in the reply (empty when none, or when it is deleted)
 * @param taggedCars the cars tagged in the reply (empty when none, or when it is deleted); every
 *        car's owner is among {@code taggedPeople} unless it belongs to the reply's author
 * @param likesCount how many users liked this reply
 * @param replyCount the number of direct children (fetched via {@code GET /replies/{id}/replies})
 * @param deleted whether the reply is soft-deleted
 * @param isAuthor whether this reply's author is the thread's original poster (drives the "Author"
 *        badge next to the username); always {@code false} for a deleted reply
 * @param viewerHasLiked whether the requesting user liked this reply
 * @param createdAt when the reply was created
 */
public record ReplyDto(
        UUID id,
        ProfileSearchResultDto author,
        String content,
        List<ProfileSearchResultDto> taggedPeople,
        List<CarSummaryDto> taggedCars,
        int likesCount,
        int replyCount,
        boolean deleted,
        @JsonProperty("is_author") boolean isAuthor,
        boolean viewerHasLiked,
        Instant createdAt
) {}
