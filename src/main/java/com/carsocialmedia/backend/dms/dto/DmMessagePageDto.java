package com.carsocialmedia.backend.dms.dto;

import java.util.List;
import java.util.UUID;

/**
 * One keyset page of a conversation's messages, newest first. The client echoes {@code nextCursor}
 * back as {@code ?cursor=} for the next (older) page; {@code null} means this was the last page.
 *
 * @param peerLastReadMessageId the peer's read watermark: every own message up to and including
 *        this id has been read (drives the "read" ticks); {@code null} if the peer never read
 */
public record DmMessagePageDto(
        List<DmMessageDto> items,
        String nextCursor,
        UUID peerLastReadMessageId
) {}
