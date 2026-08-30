package com.tweakdapp.backend.tags.dto;

import java.util.List;

/**
 * One page of a profile's tags feed (keyset pagination).
 *
 * <p>A page can come back slightly shorter than the requested size even when more items exist: the
 * four tag streams are merged and de-duplicated after they are read, so an item tagged twice (the
 * user and their car) collapses into one row. {@code nextCursor} is the only reliable
 * end-of-feed signal — {@code null} means there is nothing more.
 *
 * @param items the tagged content in this page, most recently tagged first
 * @param nextCursor opaque token to fetch the next page, or {@code null} if this is the last page
 */
public record TaggedItemPageDto(
        List<TaggedItemDto> items,
        String nextCursor
) {}
