package com.tweakdapp.backend.mapevents.internal;

import com.tweakdapp.backend.garage.dto.PublicCarEventDto;
import com.tweakdapp.backend.mapevents.dto.CarEventHistoryEventDto;
import com.tweakdapp.backend.mapevents.dto.CarEventHistoryItemDto;
import com.tweakdapp.backend.mapevents.dto.CarEventPlacementDto;
import com.tweakdapp.backend.mapevents.dto.ContestCategoryDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The public car page's events: the app's car history, with every id left behind. */
class PublicCarEventsAdapterTest {

    private static final UUID CAR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Test
    void mapsTheCarsHistoryIntoThePublicShape() {
        MapEventContestsServiceImpl contests = mock(MapEventContestsServiceImpl.class);
        when(contests.historyFor(CAR_ID)).thenReturn(List.of(new CarEventHistoryItemDto(
                new CarEventHistoryEventDto(UUID.randomUUID(), "Waterside Show", "https://media.tweakdapp.com/e.jpg",
                        "Waterside Quay", Instant.parse("2026-05-24T10:00:00Z"), null, "previous"),
                "accepted",
                List.of(new CarEventPlacementDto(UUID.randomUUID(), "Best modified",
                        new ContestCategoryDto("custom", "Custom", "trophy"), 1, 42, 120,
                        Instant.parse("2026-05-24T18:00:00Z"))))));

        List<PublicCarEventDto> events = new PublicCarEventsAdapter(contests).findForCar(CAR_ID);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.title()).isEqualTo("Waterside Show");
            assertThat(event.coverImageUrl()).isEqualTo("https://media.tweakdapp.com/e.jpg");
            assertThat(event.locationName()).isEqualTo("Waterside Quay");
            assertThat(event.status()).isEqualTo("previous");
            assertThat(event.placements()).singleElement().satisfies(p -> {
                assertThat(p.contestTitle()).isEqualTo("Best modified");
                assertThat(p.categoryIcon()).isEqualTo("trophy");
                assertThat(p.rank()).isEqualTo(1);
            });
        });
    }
}
