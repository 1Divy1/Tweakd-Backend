package com.tweakdapp.backend.forums.dto;

import java.util.List;

/**
 * One keyset page of results. The client echoes {@code nextCursor} back as {@code ?cursor=} to
 * fetch the next page; a {@code null} cursor means this was the last page. The token is opaque —
 * it encodes the active sort's key plus the tiebreaker id.
 *
 * @param items the items in this page
 * @param nextCursor opaque token for the next page, or {@code null} if this is the last page
 * @param <T> the item type
 */
public record CursorPage<T>(
        List<T> items,
        String nextCursor
) {}
