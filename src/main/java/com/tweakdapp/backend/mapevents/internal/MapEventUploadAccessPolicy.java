package com.tweakdapp.backend.mapevents.internal;

import com.tweakdapp.backend.mapevents.exception.MapEventNotFoundException;
import com.tweakdapp.backend.mapevents.exception.NotEventOrganizerException;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventOrganizerRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventRepository;
import com.tweakdapp.backend.storage.UploadAccessPolicy;
import com.tweakdapp.backend.storage.UploadTarget;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Only an event's individual organizers may upload its cover. */
@Component
class MapEventUploadAccessPolicy implements UploadAccessPolicy {

    private final MapEventRepository eventRepository;
    private final MapEventOrganizerRepository organizerRepository;

    MapEventUploadAccessPolicy(MapEventRepository eventRepository,
                               MapEventOrganizerRepository organizerRepository) {
        this.eventRepository = eventRepository;
        this.organizerRepository = organizerRepository;
    }

    @Override
    public UploadTarget target() {
        return UploadTarget.MAP_EVENT;
    }

    @Override
    public void requireUploadAccess(UUID userId, UUID eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new MapEventNotFoundException(eventId);
        }
        if (!organizerRepository.existsByEventIdAndIndividualOrganizerId(eventId, userId)) {
            throw new NotEventOrganizerException("You do not organize this event");
        }
    }
}
