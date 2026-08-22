package com.tweakdapp.backend.posts.dto;

import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;

import java.util.List;

/**
 * One page of the users who liked a post (keyset pagination), most recent liker first.
 *
 * @param items the likers in this page (id, username, avatarUrl)
 * @param nextCursor opaque token to fetch the next page, or {@code null} if this is the last page
 */
public record LikerPageDto(
        List<ProfileSearchResultDto> items,
        String nextCursor
) {}