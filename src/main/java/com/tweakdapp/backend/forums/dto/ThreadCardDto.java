package com.tweakdapp.backend.forums.dto;

import com.tweakdapp.backend.garage.dto.CarBrandDto;
import com.tweakdapp.backend.garage.dto.CarModelDto;
import com.tweakdapp.backend.garage.dto.CarSummaryDto;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A thread as it appears in a list (feed, brand/model hub, topic hub). The {@code author} resolves
 * through the profile module; {@code brand} / {@code model} through the garage module (either may be
 * {@code null}); {@code topics} are the thread's topic chips.
 *
 * <p>A "deleted" thread is only <em>anonymized</em>: it stays listed with its title, body, and
 * replies intact, but {@code deleted == true} and {@code author} is {@code null} (the client
 * renders "[deleted]" for the author).
 *
 * @param id the thread id
 * @param title the thread title
 * @param author the thread author (id, username, avatarUrl), or {@code null} when the thread is deleted
 * @param brand the car brand, or {@code null} for a general thread
 * @param model the car model, or {@code null} when the thread is brand-level or general
 * @param topics the thread's topics
 * @param taggedPeople the profiles tagged in the thread (empty when none)
 * @param taggedCars the cars tagged in the thread (empty when none); every car's owner is among
 *        {@code taggedPeople} unless it belongs to the thread's author
 * @param likesCount how many users liked the thread
 * @param replyCount how many replies the thread has
 * @param lastActivityAt when the thread last saw activity (drives the "active" sort)
 * @param pinned whether a moderator pinned the thread
 * @param locked whether the thread is locked to new replies
 * @param deleted whether the author deleted (anonymized) the thread
 * @param viewerHasSaved whether the requesting user has saved (bookmarked) this thread
 */
public record ThreadCardDto(
        UUID id,
        String title,
        ProfileSearchResultDto author,
        CarBrandDto brand,
        CarModelDto model,
        List<TopicDto> topics,
        List<ProfileSearchResultDto> taggedPeople,
        List<CarSummaryDto> taggedCars,
        int likesCount,
        int replyCount,
        Instant lastActivityAt,
        boolean pinned,
        boolean locked,
        boolean deleted,
        boolean viewerHasSaved
) {}
