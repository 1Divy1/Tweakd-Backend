package com.tweakdapp.backend.reputation.dto;

import java.util.List;

/**
 * One keyset page of a user's reputation history, newest first. The client echoes
 * {@code nextCursor} back as {@code ?cursor=} for the next page; {@code null} means this was the
 * last page.
 */
public record ReputationHistoryPageDto(
        List<ReputationEntryDto> items,
        String nextCursor
) {}
