package com.tweakdapp.backend.mapevents.internal;

import com.tweakdapp.backend.mapevents.exception.MapEventNotFoundException;
import com.tweakdapp.backend.mapevents.exception.NotEventOrganizerException;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventOrganizerRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Event cover upload URLs go to the event's individual organizers and nobody else. */
class MapEventUploadAccessPolicyTest {

    private static final UUID ORGANIZER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");

    private MapEventRepository eventRepository;
    private MapEventOrganizerRepository organizerRepository;
    private MapEventUploadAccessPolicy policy;

    @BeforeEach
    void setUp() {
        eventRepository = mock(MapEventRepository.class);
        organizerRepository = mock(MapEventOrganizerRepository.class);
        policy = new MapEventUploadAccessPolicy(eventRepository, organizerRepository);

        when(eventRepository.existsById(EVENT_ID)).thenReturn(true);
        when(organizerRepository.existsByEventIdAndIndividualOrganizerId(EVENT_ID, ORGANIZER)).thenReturn(true);
    }

    @Test
    void anOrganizerMayUpload() {
        assertThatCode(() -> policy.requireUploadAccess(ORGANIZER, EVENT_ID)).doesNotThrowAnyException();
    }

    @Test
    void anyoneElseIsForbidden() {
        assertThatThrownBy(() -> policy.requireUploadAccess(STRANGER, EVENT_ID))
                .isInstanceOf(NotEventOrganizerException.class);
    }

    @Test
    void anUnknownEventIsNotFound() {
        assertThatThrownBy(() -> policy.requireUploadAccess(ORGANIZER, UUID.randomUUID()))
                .isInstanceOf(MapEventNotFoundException.class);
    }
}
