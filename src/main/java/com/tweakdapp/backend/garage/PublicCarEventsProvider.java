package com.tweakdapp.backend.garage;

import com.tweakdapp.backend.garage.dto.PublicCarEventDto;

import java.util.List;
import java.util.UUID;

/**
 * The events a car attended, for its public page.
 *
 * <p>Garage owns the public car page but not events, and it cannot depend on the Map Events module:
 * that module already depends on this one. So garage declares what it needs and Map Events
 * implements it. It's the same arrangement as {@code storage.UploadAccessPolicy}. The whole page
 * stays one request and one response.
 */
public interface PublicCarEventsProvider {

    /** Attended events, newest first, each with its podium places. Empty when there are none. */
    List<PublicCarEventDto> findForCar(UUID carId);
}
