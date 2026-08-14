package com.carsocialmedia.backend.mapevents.dto;

import com.carsocialmedia.backend.garage.dto.CarSummaryDto;
import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;

import java.util.List;

/**
 * One pending withdrawal request on an event's organizer review queue. {@code car_event_participants}
 * has one row per car, but a withdrawal is requested and decided per owner, not per car — so this
 * groups every one of the owner's withdrawn rows under the single note they gave.
 *
 * @param owner the participant who requested the withdrawal
 * @param cars  every car of theirs currently withdrawn from this event
 * @param note  the reason they gave, or {@code null} if none
 */
public record MapEventWithdrawalRequestDto(
        ProfileSearchResultDto owner,
        List<CarSummaryDto> cars,
        String note
) {}