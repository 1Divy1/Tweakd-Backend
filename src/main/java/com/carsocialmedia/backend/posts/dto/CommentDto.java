package com.carsocialmedia.backend.posts.dto;

import com.carsocialmedia.backend.garage.dto.CarSummaryDto;
import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A single comment on a post, ready for rendering.
 *
 * <p>The {@code author} is resolved through the profile module's public interface. For a
 * soft-deleted comment ({@code deleted == true}) the {@code content} is {@code null} — the
 * row is still returned because it may anchor replies, and the client renders "[deleted]".
 *
 * @param id the comment ID
 * @param author the comment author (id, username, avatarUrl)
 * @param content the comment text, or {@code null} when the comment is deleted
 * @param taggedPeople the profiles tagged in the comment (empty when none, or when it is deleted)
 * @param taggedCars the cars tagged in the comment (empty when none, or when it is deleted); every
 *        car's owner is among {@code taggedPeople} unless it belongs to the comment's author
 * @param parentCommentId the parent comment for a threaded reply, or {@code null} for a root comment
 * @param deleted whether the comment is soft-deleted
 * @param likeCount how many users liked this comment
 * @param viewerHasLiked whether the requesting user liked this comment
 * @param createdAt when the comment was created
 * @param replyCount the number of child comments having the current comment as their parent
 */
public record CommentDto(
        UUID id,
        ProfileSearchResultDto author,
        String content,
        List<ProfileSearchResultDto> taggedPeople,
        List<CarSummaryDto> taggedCars,
        UUID parentCommentId,
        boolean deleted,
        long likeCount,
        boolean viewerHasLiked,
        Instant createdAt,
        int replyCount
) {}