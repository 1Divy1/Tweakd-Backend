package com.tweakdapp.backend.mapevents.internal;

import com.tweakdapp.backend.garage.PublicCarEventsProvider;
import com.tweakdapp.backend.garage.dto.PublicCarEventDto;
import com.tweakdapp.backend.garage.dto.PublicCarPlacementDto;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Feeds the car's event history into its public page ({@code GET /public/v1/cars/{code}}).
 *
 * <p>This is the same history the app shows on a car. It has the same filters: approved events
 * only, never cancelled ones, only live or finished, and podium places only from finished contests.
 * It's mapped into garage's public records, which leave out every id: event, contest and category.
 */
@Component
class PublicCarEventsAdapter implements PublicCarEventsProvider {

    private final MapEventContestsServiceImpl contests;

    PublicCarEventsAdapter(MapEventContestsServiceImpl contests) {
        this.contests = contests;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicCarEventDto> findForCar(UUID carId) {
        return contests.historyFor(carId).stream()
                .map(item -> new PublicCarEventDto(
                        item.event().title(),
                        item.event().coverImageUrl(),
                        item.event().locationName(),
                        item.event().startsAt(),
                        item.event().status(),
                        item.placements().stream()
                                .map(p -> new PublicCarPlacementDto(
                                        p.title(),
                                        p.category() == null ? null : p.category().icon(),
                                        p.finalRank()))
                                .toList()))
                .toList();
    }
}
