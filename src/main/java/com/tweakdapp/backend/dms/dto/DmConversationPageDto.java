package com.tweakdapp.backend.dms.dto;

import java.util.List;

/**
 * One keyset page of the caller's chats list, most recently active first. The client echoes
 * {@code nextCursor} back as {@code ?cursor=}; {@code null} means this was the last page (an empty
 * first page is the "no previous chats" state).
 */
public record DmConversationPageDto(
        List<DmConversationDto> items,
        String nextCursor
) {}
