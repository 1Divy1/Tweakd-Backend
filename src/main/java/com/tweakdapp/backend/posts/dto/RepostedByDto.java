package com.tweakdapp.backend.posts.dto;

import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;

import java.util.List;

/**
 * Who, among the accounts the viewer follows, reposted a post — the "Andrei and 2 others reposted"
 * line above a feed card.
 *
 * @param users      up to {@code 2} of them, most recent repost first
 * @param totalCount how many followed accounts reposted it in all ({@code >= users.size()})
 */
public record RepostedByDto(
        List<ProfileSearchResultDto> users,
        long totalCount
) {}
