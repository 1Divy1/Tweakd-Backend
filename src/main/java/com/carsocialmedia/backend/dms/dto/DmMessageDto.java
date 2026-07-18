package com.carsocialmedia.backend.dms.dto;

import com.carsocialmedia.backend.garage.dto.CarSummaryDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One DM. A deleted message keeps its place in the conversation but {@code deleted == true},
 * {@code content} is empty and {@code taggedCars} is empty — the client renders a "message deleted"
 * placeholder for both sides.
 *
 * <p>{@code taggedCars} reuses the garage module's {@link CarSummaryDto} (same convention as posts'
 * {@code PostDto.taggedCars}); it is resolved through the garage public API, never by crossing into
 * its internals. A car deleted from its owner's garage silently disappears from old messages (the
 * {@code dm_message_car_tags} row is removed by an ON DELETE CASCADE).
 *
 * @param taggedCars cars tagged in the message (empty when none, and always empty once deleted)
 */
public record DmMessageDto(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String content,
        boolean deleted,
        Instant createdAt,
        List<CarSummaryDto> taggedCars
) {}
