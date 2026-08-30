package com.tweakdapp.backend.forums.dto;

import com.tweakdapp.backend.garage.dto.CarBrandDto;
import com.tweakdapp.backend.garage.dto.CarModelDto;
import com.tweakdapp.backend.garage.dto.CarSummaryDto;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A single thread's detail view: the card fields plus the OP body, creation time, and whether the
 * requesting user has liked it. Replies are fetched separately (paginated).
 *
 * <p>A "deleted" thread is only <em>anonymized</em>: it stays readable (and repliable) with its
 * title, body, and replies intact, but {@code deleted == true} and {@code author} is {@code null}.
 *
 * @param id the thread id
 * @param title the thread title
 * @param content the OP body, or {@code null} if none
 * @param author the thread author, or {@code null} when the thread is deleted
 * @param brand the car brand, or {@code null}
 * @param model the car model, or {@code null}
 * @param topics the thread's topics
 * @param taggedPeople the profiles tagged in the thread (empty when none)
 * @param taggedCars the cars tagged in the thread (empty when none); every car's owner is among
 *        {@code taggedPeople} unless it belongs to the thread's author
 * @param likesCount how many users liked the thread
 * @param replyCount how many replies the thread has
 * @param createdAt when the thread was created
 * @param lastActivityAt when the thread last saw activity
 * @param pinned whether a moderator pinned the thread
 * @param locked whether the thread is locked to new replies
 * @param deleted whether the author deleted (anonymized) the thread
 * @param viewerHasLiked whether the requesting user liked this thread
 * @param viewerHasSaved whether the requesting user has saved (bookmarked) this thread
 */
public record ThreadDetailDto(
        UUID id,
        String title,
        String content,
        ProfileSearchResultDto author,
        CarBrandDto brand,
        CarModelDto model,
        List<TopicDto> topics,
        List<ProfileSearchResultDto> taggedPeople,
        List<CarSummaryDto> taggedCars,
        int likesCount,
        int replyCount,
        Instant createdAt,
        Instant lastActivityAt,
        boolean pinned,
        boolean locked,
        boolean deleted,
        boolean viewerHasLiked,
        boolean viewerHasSaved
) {}
