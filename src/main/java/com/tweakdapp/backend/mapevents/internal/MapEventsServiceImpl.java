package com.tweakdapp.backend.mapevents.internal;

import com.tweakdapp.backend.business.BusinessService;
import com.tweakdapp.backend.business.dto.BusinessRefDto;
import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.garage.dto.CarSummaryDto;
import com.tweakdapp.backend.mapevents.MapEventApprovedEvent;
import com.tweakdapp.backend.mapevents.MapEventCarDecidedEvent;
import com.tweakdapp.backend.mapevents.MapEventCarRegisteredEvent;
import com.tweakdapp.backend.mapevents.MapEventOrganizerAddedEvent;
import com.tweakdapp.backend.mapevents.MapEventRejectedEvent;
import com.tweakdapp.backend.mapevents.MapEventWithdrawalDecidedEvent;
import com.tweakdapp.backend.mapevents.MapEventWithdrawalRequestedEvent;
import com.tweakdapp.backend.mapevents.MapEventsService;
import com.tweakdapp.backend.mapevents.dto.CarMeetDetailsDto;
import com.tweakdapp.backend.mapevents.dto.GeocodeCandidateDto;
import com.tweakdapp.backend.mapevents.dto.MapEventAttendeeDto;
import com.tweakdapp.backend.mapevents.dto.MapEventCategoryDto;
import com.tweakdapp.backend.mapevents.dto.MapEventDto;
import com.tweakdapp.backend.mapevents.dto.MapEventOrganizerDto;
import com.tweakdapp.backend.mapevents.dto.MapEventPageDto;
import com.tweakdapp.backend.mapevents.dto.MapEventParticipantDto;
import com.tweakdapp.backend.mapevents.dto.MapEventPinDto;
import com.tweakdapp.backend.mapevents.dto.MapEventRuleDto;
import com.tweakdapp.backend.mapevents.dto.MapEventSummaryDto;
import com.tweakdapp.backend.mapevents.dto.MapEventViewerStateDto;
import com.tweakdapp.backend.mapevents.dto.MapEventWithdrawalRequestDto;
import com.tweakdapp.backend.mapevents.dto.OrganizerCandidateDto;
import com.tweakdapp.backend.mapevents.dto.request.AddOrganizerRequest;
import com.tweakdapp.backend.mapevents.dto.request.CreateMapEventRequest;
import com.tweakdapp.backend.mapevents.dto.request.GeocodeQuery;
import com.tweakdapp.backend.mapevents.dto.request.UpdateMapEventRequest;
import com.tweakdapp.backend.mapevents.exception.CarNotOwnedException;
import com.tweakdapp.backend.mapevents.exception.EventClosedException;
import com.tweakdapp.backend.mapevents.exception.EventNotEditableException;
import com.tweakdapp.backend.mapevents.exception.InvalidMapEventException;
import com.tweakdapp.backend.mapevents.exception.InvalidSearchAreaException;
import com.tweakdapp.backend.mapevents.exception.MapEventNotFoundException;
import com.tweakdapp.backend.mapevents.exception.NotEventOrganizerException;
import com.tweakdapp.backend.mapevents.internal.entities.CarMeetEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventAttendeeEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventAttendeeId;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventCategoryEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventOrganizerEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventParticipantEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventParticipantId;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventRuleEntity;
import com.tweakdapp.backend.mapevents.internal.repositories.CarMeetRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.ContestEntryRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventAttendeeRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventCategoryRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventOrganizerRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventParticipantRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventRuleRepository;
import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import com.tweakdapp.backend.shared.geo.GeoSupport;
import com.tweakdapp.backend.storage.StorageBucket;
import com.tweakdapp.backend.storage.StorageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
class MapEventsServiceImpl implements MapEventsService {

    private static final Logger log = LoggerFactory.getLogger(MapEventsServiceImpl.class);

    /**
     * Widest radius a single map query may cover. Beyond this the spatial index stops being
     * selective and the query degrades into a scan; the map has no use for it either.
     */
    private static final double MAX_RADIUS_KM = 500.0;

    /** Hard ceiling on pins per request, so a hand-crafted {@code limit} cannot pull the table. */
    private static final int MAX_LIMIT = 500;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final MapEventRepository eventRepository;
    private final MapEventCategoryRepository categoryRepository;
    private final CarMeetRepository carMeetRepository;
    private final MapEventOrganizerRepository organizerRepository;
    private final MapEventAttendeeRepository attendeeRepository;
    private final MapEventParticipantRepository participantRepository;
    private final MapEventRuleRepository ruleRepository;
    private final ProfileService profileService;
    private final GarageService garageService;
    private final BusinessService businessService;
    private final StorageService storageService;
    private final ApplicationEventPublisher events;
    private final MapboxGeocodingClient mapboxGeocodingClient;
    private final ContestFinalizer contestFinalizer;
    private final ContestEntryRepository contestEntryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    MapEventsServiceImpl(MapEventRepository eventRepository,
                         MapEventCategoryRepository categoryRepository,
                         CarMeetRepository carMeetRepository,
                         MapEventOrganizerRepository organizerRepository,
                         MapEventAttendeeRepository attendeeRepository,
                         MapEventParticipantRepository participantRepository,
                         MapEventRuleRepository ruleRepository,
                         ProfileService profileService,
                         GarageService garageService,
                         BusinessService businessService,
                         StorageService storageService,
                         ApplicationEventPublisher events,
                         MapboxGeocodingClient mapboxGeocodingClient,
                         ContestFinalizer contestFinalizer,
                         ContestEntryRepository contestEntryRepository) {
        this.eventRepository = eventRepository;
        this.categoryRepository = categoryRepository;
        this.carMeetRepository = carMeetRepository;
        this.organizerRepository = organizerRepository;
        this.attendeeRepository = attendeeRepository;
        this.participantRepository = participantRepository;
        this.ruleRepository = ruleRepository;
        this.profileService = profileService;
        this.garageService = garageService;
        this.businessService = businessService;
        this.storageService = storageService;
        this.events = events;
        this.mapboxGeocodingClient = mapboxGeocodingClient;
        this.contestFinalizer = contestFinalizer;
        this.contestEntryRepository = contestEntryRepository;
    }

    // ==================================================================
    // Map
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public List<MapEventPinDto> findNearby(double lat, double lng, double radiusKm, String categoryId, int limit) {
        validateSearchArea(lat, lng, radiusKm, limit);

        String normalisedCategory = (categoryId == null || categoryId.isBlank()) ? null : categoryId.trim();

        return eventRepository.findVisibleNearby(lat, lng, radiusKm * 1000.0, normalisedCategory, limit)
                .stream()
                .map(row -> new MapEventPinDto(
                        row.getId(),
                        row.getTitle(),
                        row.getCategoryId(),
                        row.getCategoryLabel(),
                        row.getLat(),
                        row.getLng(),
                        row.getLocationName(),
                        resolveCoverUrl(row.getCoverImageKey()),
                        row.getStartsAt(),
                        row.getEndsAt(),
                        row.getStatus(),
                        row.getAttendeesCount(),
                        row.getAttendingCarsCount(),
                        row.getMaxParticipantCapacity()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MapEventCategoryDto> listCategories() {
        return categoryRepository.findByAvailableTrueOrderByLabelAsc()
                .stream()
                .map(category -> new MapEventCategoryDto(category.getId(), category.getLabel()))
                .toList();
    }

    // No @Transactional here: this method never touches the database, only an outbound HTTP call
    // to Mapbox, and the pool is sized to 2 connections (application.yaml) — holding one idle for
    // the round-trip would be wasteful at best.
    @Override
    public List<GeocodeCandidateDto> geocode(GeocodeQuery query, Double proximityLat, Double proximityLng) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        Double lat = sanitizeCoordinate(proximityLat, -90, 90);
        Double lng = sanitizeCoordinate(proximityLng, -180, 180);
        // Bias only applies when both halves of the pair check out; a lone or malformed value is
        // dropped rather than failing the whole search, since it is only a relevance hint.
        if (lat == null || lng == null) {
            lat = null;
            lng = null;
        }

        return mapboxGeocodingClient.forwardGeocode(query, lat, lng).stream()
                .filter(feature -> feature.geometry() != null && feature.geometry().coordinates() != null
                        && feature.geometry().coordinates().size() == 2 && feature.properties() != null)
                .map(feature -> {
                    MapboxGeocodingClient.MapboxProperties props = feature.properties();
                    String placeName = props.fullAddress() != null ? props.fullAddress()
                            : props.placeFormatted() != null ? props.placeFormatted()
                            : props.name();
                    String accuracy = props.coordinates() == null ? null : props.coordinates().accuracy();
                    return new GeocodeCandidateDto(
                            feature.geometry().coordinates().get(1),
                            feature.geometry().coordinates().get(0),
                            placeName,
                            props.featureType(),
                            accuracy);
                })
                .toList();
    }

    // ==================================================================
    // Event page
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public MapEventDto getEvent(UUID currentUserId, UUID eventId) {
        MapEventEntity event = loadEvent(eventId);
        boolean organizer = isOrganizer(eventId, currentUserId);

        // An unapproved event exists only for the people running it. Anyone else gets the same 404
        // as for a nonexistent id, so submissions cannot be discovered by probing.
        if (!event.isApproved() && !organizer) {
            throw new MapEventNotFoundException(eventId);
        }
        return assemble(event, currentUserId, organizer);
    }

    @Override
    @Transactional(readOnly = true)
    public MapEventDto getEventAsAdmin(UUID eventId) {
        // Reviewers see every event whatever its approval state — that is the point of the queue —
        // and always with the organizer's view, so the rejection reason is visible.
        return assemble(loadEvent(eventId), null, true);
    }

    @Override
    @Transactional(readOnly = true)
    public MapEventPageDto<MapEventAttendeeDto> listAttendees(UUID currentUserId, UUID eventId, String status,
                                                              String cursor, int size) {
        requireVisible(currentUserId, eventId);

        String normalisedStatus = normaliseAttendanceStatus(status, true);
        int pageSize = clampPageSize(size);
        MapEventCursor decoded = MapEventCursor.decode(cursor);

        List<MapEventAttendeeEntity> rows = attendeeRepository.findPage(
                eventId,
                normalisedStatus,
                decoded == null ? null : decoded.timestamp(),
                decoded == null ? null : decoded.id(),
                Limit.of(pageSize + 1));

        boolean hasMore = rows.size() > pageSize;
        List<MapEventAttendeeEntity> page = hasMore ? rows.subList(0, pageSize) : rows;

        Map<UUID, ProfileSearchResultDto> profiles = resolveProfiles(
                page.stream().map(row -> row.getId().getUserId()).toList());

        List<MapEventAttendeeDto> items = page.stream()
                .map(row -> new MapEventAttendeeDto(
                        profiles.get(row.getId().getUserId()),
                        row.getStatus()))
                // A profile that no longer resolves (deleted account mid-page) is dropped rather
                // than rendered as a blank row.
                .filter(dto -> dto.profile() != null)
                .toList();

        return new MapEventPageDto<>(items, nextCursor(hasMore, page,
                MapEventAttendeeEntity::getCreatedAt, row -> row.getId().getUserId()));
    }

    @Override
    @Transactional(readOnly = true)
    public MapEventPageDto<MapEventParticipantDto> listParticipants(UUID currentUserId, UUID eventId, String status,
                                                                    String cursor, int size) {
        requireVisible(currentUserId, eventId);

        String normalisedStatus = normaliseParticipantStatus(status);
        // Pending and rejected entries are the organizers' business: exposing them would tell
        // everyone whose car was turned away. A withdrawal request does not carry that stigma —
        // the participant asked to leave themselves — so it is as public as accepted.
        boolean publiclyVisible = normalisedStatus == null
                || MapEventParticipantEntity.ACCEPTED.equals(normalisedStatus)
                || MapEventParticipantEntity.WITHDRAWN.equals(normalisedStatus);
        if (!publiclyVisible && !isOrganizer(eventId, currentUserId)) {
            throw new NotEventOrganizerException(
                    "Only organizers can view cars that are not in the accepted line-up");
        }

        int pageSize = clampPageSize(size);
        MapEventCursor decoded = MapEventCursor.decode(cursor);
        Instant cursorCreatedAt = decoded == null ? null : decoded.timestamp();
        UUID cursorCarId = decoded == null ? null : decoded.id();

        // No filter = the public line-up, which now includes withdrawn cars alongside accepted
        // ones (a pending withdrawal does not remove a participant from the entry list).
        List<MapEventParticipantEntity> rows = normalisedStatus == null
                ? participantRepository.findLineupPage(eventId, cursorCreatedAt, cursorCarId, Limit.of(pageSize + 1))
                : participantRepository.findPage(eventId, normalisedStatus, cursorCreatedAt, cursorCarId, Limit.of(pageSize + 1));

        boolean hasMore = rows.size() > pageSize;
        List<MapEventParticipantEntity> page = hasMore ? rows.subList(0, pageSize) : rows;

        Map<UUID, CarSummaryDto> cars = resolveCars(
                page.stream().map(row -> row.getId().getCarId()).toList());

        List<MapEventParticipantDto> items = page.stream()
                .map(row -> new MapEventParticipantDto(
                        cars.get(row.getId().getCarId()),
                        row.getStatus(),
                        row.getCreatedAt(),
                        row.getRejectionReason()))
                .filter(dto -> dto.car() != null)
                .toList();

        return new MapEventPageDto<>(items, nextCursor(hasMore, page,
                MapEventParticipantEntity::getCreatedAt, row -> row.getId().getCarId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MapEventParticipantDto> listMyParticipants(UUID currentUserId, UUID eventId) {
        requireVisible(currentUserId, eventId);
        List<MapEventParticipantEntity> rows = participantRepository.findByIdEventIdAndOwnerId(eventId, currentUserId);
        return toParticipantDtos(rows);
    }

    @Override
    @Transactional(readOnly = true)
    public MapEventPageDto<MapEventSummaryDto> getMyEvents(UUID currentUserId, String cursor, int size) {
        int pageSize = clampPageSize(size);
        MapEventCursor decoded = MapEventCursor.decode(cursor);

        List<MapEventEntity> rows = eventRepository.findMine(
                currentUserId,
                decoded == null ? null : decoded.timestamp(),
                decoded == null ? null : decoded.id(),
                Limit.of(pageSize + 1));

        return toSummaryPage(rows, pageSize);
    }

    // ==================================================================
    // Authoring
    // ==================================================================

    @Override
    @Transactional
    public MapEventDto createEvent(UUID currentUserId, CreateMapEventRequest request) {
        MapEventCategoryEntity category = categoryRepository.findById(request.categoryId())
                .filter(MapEventCategoryEntity::isAvailable)
                .orElseThrow(() -> new InvalidMapEventException(
                        "Unknown or unavailable event category: " + request.categoryId()));

        Instant now = Instant.now();
        validateTimings(request.startsAt(), request.endsAt(), now);
        validateCoordinates(request.lat(), request.lng());

        MapEventEntity event = new MapEventEntity();
        event.setId(UUID.randomUUID());
        event.setCategory(category);
        event.setTitle(request.title().trim());
        event.setDescription(request.description().trim());
        event.setLocationName(request.locationName().trim());
        event.setStartsAt(request.startsAt());
        event.setEndsAt(request.endsAt());
        event.setLocation(GeoSupport.point(request.lat(), request.lng()));
        event.setStatus(MapEventEntity.STATUS_UPCOMING);
        event.setApprovalStatus(MapEventEntity.APPROVAL_PENDING);
        event.setRequiresParticipantApproval(
                request.requiresParticipantApproval() == null || request.requiresParticipantApproval());
        event.setMaxParticipantCapacity(request.maxParticipantCapacity());
        event.setCreatedBy(currentUserId);

        // event.id is set client-side, so save() merges rather than persists, returning a new
        // managed instance — reassign, since the original `event` reference stays detached.
        event = eventRepository.saveAndFlush(event);
        // created_at is DB-managed (DEFAULT now()), so the in-memory entity does not have it until
        // we read it back. The create response carries it, hence the refresh.
        entityManager.refresh(event);

        // The creator is an organizer too, so the roster is complete on its own; `role` is what
        // distinguishes them, and a partial unique index allows only one creator per event.
        MapEventOrganizerEntity creator = new MapEventOrganizerEntity();
        creator.setId(UUID.randomUUID());
        creator.setEventId(event.getId());
        creator.setIndividualOrganizerId(currentUserId);
        creator.setRole(MapEventOrganizerEntity.ROLE_CREATOR);
        organizerRepository.save(creator);

        if (MapEventCategoryEntity.CAR_MEET.equals(category.getId())) {
            saveCarMeetDetails(event, request.registrationDeadline(), true);
        }

        if (request.rules() != null && !request.rules().isEmpty()) {
            insertRules(event.getId(), request.rules());
        }

        return assemble(event, currentUserId, true);
    }

    @Override
    @Transactional
    public MapEventDto updateEvent(UUID currentUserId, UUID eventId, UpdateMapEventRequest request) {
        MapEventEntity event = loadEvent(eventId);
        requireOrganizer(event, currentUserId);
        requireEditable(event);

        if (request.title() != null) {
            event.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            event.setDescription(request.description().trim());
        }
        if (request.locationName() != null) {
            event.setLocationName(request.locationName().trim());
        }
        if (request.lat() != null || request.lng() != null) {
            if (request.lat() == null || request.lng() == null) {
                throw new InvalidMapEventException("lat and lng must be supplied together");
            }
            validateCoordinates(request.lat(), request.lng());
            event.setLocation(GeoSupport.point(request.lat(), request.lng()));
        }

        Instant startsAt = request.startsAt() != null ? request.startsAt() : event.getStartsAt();
        // An explicit null cannot be distinguished from "absent" in a record, so endsAt can be
        // changed but not cleared; recreating the event is the way to drop an end time.
        Instant endsAt = request.endsAt() != null ? request.endsAt() : event.getEndsAt();
        if (request.startsAt() != null || request.endsAt() != null) {
            validateTimings(startsAt, endsAt, Instant.now());
            event.setStartsAt(startsAt);
            event.setEndsAt(endsAt);
        }
        if (request.requiresParticipantApproval() != null) {
            event.setRequiresParticipantApproval(request.requiresParticipantApproval());
        }
        if (request.maxParticipantCapacity() != null) {
            event.setMaxParticipantCapacity(request.maxParticipantCapacity());
        }
        if (request.registrationDeadline() != null) {
            if (!MapEventCategoryEntity.CAR_MEET.equals(event.getCategory().getId())) {
                throw new InvalidMapEventException(
                        "registration_deadline only applies to car meet events");
            }
            saveCarMeetDetails(event, request.registrationDeadline(), false);
        }

        // Editing a rejected event is how it gets resubmitted: the reason no longer applies.
        if (MapEventEntity.APPROVAL_REJECTED.equals(event.getApprovalStatus())) {
            event.setApprovalStatus(MapEventEntity.APPROVAL_PENDING);
            event.setRejectionReason(null);
        }

        eventRepository.save(event);
        return assemble(event, currentUserId, true);
    }

    @Override
    @Transactional
    public MapEventDto saveCoverImageKey(UUID currentUserId, UUID eventId, String key) {
        MapEventEntity event = loadEvent(eventId);
        requireOrganizer(event, currentUserId);
        requireEditable(event);

        String trimmed = key.trim();
        // The key is minted server-side under events/{eventId}/, so requiring that prefix stops one
        // event from claiming an object uploaded for another.
        String expectedPrefix = "events/" + eventId + "/";
        if (!trimmed.startsWith(expectedPrefix)) {
            throw new InvalidMapEventException("Cover image key must start with " + expectedPrefix);
        }

        String previous = event.getCoverImageKey();
        event.setCoverImageKey(trimmed);
        eventRepository.save(event);

        if (previous != null && !previous.equals(trimmed)) {
            deleteR2ObjectsAfterCommit(List.of(previous), eventId);
        }
        return assemble(event, currentUserId, true);
    }

    @Override
    @Transactional
    public MapEventDto replaceRules(UUID currentUserId, UUID eventId, List<String> rules) {
        MapEventEntity event = loadEvent(eventId);
        requireOrganizer(event, currentUserId);
        requireEditable(event);

        // The (event_id, sort_order) unique index means the old rows must be gone before the new
        // ones land; deleteByEventId is a real DELETE statement, executed immediately rather than
        // queued, so the insert below never collides with it.
        ruleRepository.deleteByEventId(eventId);
        insertRules(eventId, rules);

        return assemble(event, currentUserId, true);
    }

    /** Inserts a fresh set of rule rows, {@code sort_order} assigned from list position. */
    private void insertRules(UUID eventId, List<String> rules) {
        List<MapEventRuleEntity> rows = new ArrayList<>(rules.size());
        short order = 0;
        for (String rule : rules) {
            MapEventRuleEntity row = new MapEventRuleEntity();
            row.setId(UUID.randomUUID());
            row.setEventId(eventId);
            row.setRule(rule.trim());
            row.setSortOrder(order++);
            rows.add(row);
        }
        ruleRepository.saveAll(rows);
    }

    @Override
    @Transactional
    public MapEventDto cancelEvent(UUID currentUserId, UUID eventId) {
        MapEventEntity event = loadEvent(eventId);
        requireOrganizer(event, currentUserId);

        event.setStatus(MapEventEntity.STATUS_CANCELED);
        eventRepository.save(event);
        // A cancelled meet has no winners: its open contests close with the standings frozen and
        // nothing paid out.
        contestFinalizer.finalizeAllOpen(event, currentUserId, false);
        return assemble(event, currentUserId, true);
    }

    @Override
    @Transactional
    public MapEventDto markFinished(UUID currentUserId, UUID eventId) {
        MapEventEntity event = loadEvent(eventId);
        requireOrganizer(event, currentUserId);

        if (MapEventEntity.STATUS_CANCELED.equals(event.getStatus())) {
            throw new EventNotEditableException("A cancelled event cannot be marked finished");
        }
        event.setStatus(MapEventEntity.STATUS_PREVIOUS);
        eventRepository.save(event);
        // Finishing the meet finishes its contests, in the same transaction, with the podium paid.
        contestFinalizer.finalizeAllOpen(event, currentUserId, true);
        return assemble(event, currentUserId, true);
    }

    @Override
    @Transactional
    public void deleteEvent(UUID currentUserId, UUID eventId) {
        MapEventEntity event = loadEvent(eventId);
        // Deletion is the creator's alone: a co-organizer can walk away, not destroy the event.
        if (!event.getCreatedBy().equals(currentUserId)) {
            throw new NotEventOrganizerException("Only the event's creator can delete it");
        }
        purge(event);
    }

    // ==================================================================
    // Organizers
    // ==================================================================

    @Override
    @Transactional
    public MapEventDto addOrganizer(UUID currentUserId, UUID eventId, AddOrganizerRequest request) {
        MapEventEntity event = loadEvent(eventId);
        requireCreator(event, currentUserId);

        boolean hasUser = request.userId() != null;
        boolean hasBusiness = request.businessId() != null;
        if (hasUser == hasBusiness) {
            throw new InvalidMapEventException(
                    "Provide exactly one of user_id or business_id");
        }

        MapEventOrganizerEntity organizer = new MapEventOrganizerEntity();
        organizer.setId(UUID.randomUUID());
        organizer.setEventId(eventId);
        organizer.setRole(MapEventOrganizerEntity.ROLE_ORGANIZER);

        if (hasUser) {
            if (profileService.findByIds(List.of(request.userId())).isEmpty()) {
                throw new InvalidMapEventException("No such user: " + request.userId());
            }
            if (organizerRepository.existsByEventIdAndIndividualOrganizerId(eventId, request.userId())) {
                throw new InvalidMapEventException("That user already organizes this event");
            }
            organizer.setIndividualOrganizerId(request.userId());
        } else {
            // Businesses have no login yet, so this is a display credit only — but it must still
            // point at a business the app is willing to show.
            if (businessService.findBusinessRefsByIds(List.of(request.businessId())).isEmpty()) {
                throw new InvalidMapEventException("No such business: " + request.businessId());
            }
            if (organizerRepository.existsByEventIdAndBusinessOrganizerId(eventId, request.businessId())) {
                throw new InvalidMapEventException("That business already organizes this event");
            }
            organizer.setBusinessOrganizerId(request.businessId());
        }

        organizerRepository.save(organizer);

        // Businesses cannot log in, so there is only somebody to tell when it was a user.
        if (hasUser && !request.userId().equals(currentUserId)) {
            events.publishEvent(new MapEventOrganizerAddedEvent(
                    eventId, event.getTitle(), currentUserId, request.userId()));
        }
        return assemble(event, currentUserId, true);
    }

    @Override
    @Transactional
    public MapEventDto removeOrganizer(UUID currentUserId, UUID eventId, UUID organizerId) {
        MapEventEntity event = loadEvent(eventId);
        requireCreator(event, currentUserId);

        MapEventOrganizerEntity organizer = organizerRepository.findByEventIdAndId(eventId, organizerId)
                .orElseThrow(() -> new InvalidMapEventException("No such organizer on this event: " + organizerId));

        if (organizer.isCreator()) {
            throw new InvalidMapEventException("The event's creator cannot be removed as organizer");
        }
        organizerRepository.delete(organizer);
        return assemble(event, currentUserId, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizerCandidateDto> searchOrganizerCandidates(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        Stream<OrganizerCandidateDto> individuals = profileService.searchByUsername(query).stream()
                .map(profile -> new OrganizerCandidateDto(
                        MapEventOrganizerDto.INDIVIDUAL, profile.id(), profile.name(), profile.username(), profile.avatarUrl()));

        Stream<OrganizerCandidateDto> businesses = businessService.searchByName(query).stream()
                .map(business -> new OrganizerCandidateDto(
                        MapEventOrganizerDto.BUSINESS, business.id(), business.name(), null, business.logoUrl()));

        return Stream.concat(individuals, businesses).toList();
    }

    // ==================================================================
    // Attending (spectators)
    // ==================================================================

    @Override
    @Transactional
    public MapEventDto setAttendance(UUID currentUserId, UUID eventId, String status) {
        MapEventEntity event = loadApprovedEvent(eventId);
        String normalised = normaliseAttendanceStatus(status, false);

        // Attendees are spectators, so RSVP stays open for as long as there is something to watch —
        // it closes only once the event has finished or been called off.
        if (event.hasFinished(Instant.now())) {
            throw new EventClosedException("This event has finished; RSVP is closed");
        }

        MapEventAttendeeId id = new MapEventAttendeeId(currentUserId, eventId);
        MapEventAttendeeEntity attendance = attendeeRepository.findById(id)
                .orElseGet(() -> {
                    MapEventAttendeeEntity fresh = new MapEventAttendeeEntity();
                    fresh.setId(id);
                    return fresh;
                });
        attendance.setStatus(normalised);
        attendeeRepository.save(attendance);

        return reloadAndAssemble(eventId, currentUserId);
    }

    @Override
    @Transactional
    public MapEventDto removeAttendance(UUID currentUserId, UUID eventId) {
        MapEventEntity event = loadEvent(eventId);
        requireVisible(currentUserId, event);

        attendeeRepository.deleteById(new MapEventAttendeeId(currentUserId, eventId));
        return reloadAndAssemble(eventId, currentUserId);
    }

    // ==================================================================
    // Participating (cars)
    // ==================================================================

    @Override
    @Transactional
    public MapEventDto registerCar(UUID currentUserId, UUID eventId, UUID carId) {
        MapEventEntity event = loadApprovedEvent(eventId);

        UUID owner = garageService.findCarOwnerIds(List.of(carId)).get(carId);
        if (owner == null || !owner.equals(currentUserId)) {
            throw new CarNotOwnedException(carId);
        }

        Instant now = Instant.now();
        if (event.hasFinished(now)) {
            throw new EventClosedException("This event has finished; registration is closed");
        }
        carMeetRepository.findById(eventId).ifPresent(meet -> {
            if (!now.isBefore(meet.getRegistrationDeadline())) {
                throw new EventClosedException("The registration deadline for this event has passed");
            }
        });

        MapEventParticipantId id = new MapEventParticipantId(eventId, carId);
        MapEventParticipantEntity participant = participantRepository.findById(id).orElse(null);
        if (participant == null) {
            // The cap applies to brand new entries only — re-sending an existing registration is a
            // no-op, not a new claim on a slot.
            Integer capacity = event.getMaxParticipantCapacity();
            if (capacity != null && event.getAttendingCarsCount() >= capacity) {
                throw new EventClosedException("This event has reached its participant capacity");
            }
            participant = new MapEventParticipantEntity();
            participant.setId(id);
            participant.setOwnerId(currentUserId);
            // An event that vets its line-up gets a pending row; otherwise the car is in.
            participant.setStatus(event.isRequiresParticipantApproval()
                    ? MapEventParticipantEntity.PENDING
                    : MapEventParticipantEntity.ACCEPTED);
        }
        participantRepository.save(participant);

        // Only worth telling the organizers when there is actually a decision waiting for them.
        if (MapEventParticipantEntity.PENDING.equals(participant.getStatus())) {
            List<UUID> organizers = individualOrganizerIds(eventId).stream()
                    .filter(organizerId -> !organizerId.equals(currentUserId))
                    .toList();
            if (!organizers.isEmpty()) {
                events.publishEvent(new MapEventCarRegisteredEvent(
                        eventId, event.getTitle(), carId, currentUserId, organizers));
            }
        }

        return reloadAndAssemble(eventId, currentUserId);
    }

    @Override
    @Transactional
    public MapEventDto withdrawCar(UUID currentUserId, UUID eventId, UUID carId) {
        MapEventParticipantId id = new MapEventParticipantId(eventId, carId);
        participantRepository.findById(id).ifPresent(participant -> {
            if (!participant.getOwnerId().equals(currentUserId)) {
                throw new CarNotOwnedException(carId);
            }
            // Nothing was ever confirmed, so there is nothing to ask an organizer's leave to
            // undo. An accepted registration must go through requestWithdrawal instead.
            if (!MapEventParticipantEntity.PENDING.equals(participant.getStatus())) {
                throw new InvalidMapEventException(
                        "Only a pending registration can be withdrawn directly; "
                                + "an accepted entry must go through a withdrawal request");
            }
            participantRepository.delete(participant);
        });
        return reloadAndAssemble(eventId, currentUserId);
    }

    @Override
    @Transactional
    public MapEventDto decideParticipant(UUID currentUserId, UUID eventId, UUID carId, String status, String reason) {
        MapEventEntity event = loadEvent(eventId);
        requireOrganizer(event, currentUserId);

        String normalised = normaliseDecision(status);
        MapEventParticipantEntity participant = participantRepository
                .findById(new MapEventParticipantId(eventId, carId))
                .orElseThrow(() -> new InvalidMapEventException(
                        "That car is not registered for this event: " + carId));

        if (MapEventParticipantEntity.WITHDRAWN.equals(participant.getStatus())) {
            throw new InvalidMapEventException(
                    "This entry has a pending withdrawal request; use the withdrawal review endpoints instead");
        }

        String trimmedReason = (reason == null || reason.isBlank()) ? null : reason.trim();
        if (MapEventParticipantEntity.REJECTED.equals(normalised) && trimmedReason == null) {
            throw new InvalidMapEventException("A rejection reason is required");
        }

        boolean wasAccepted = MapEventParticipantEntity.ACCEPTED.equals(participant.getStatus());
        participant.setStatus(normalised);
        // Accepting clears any reason left over from a past rejection of the same car.
        participant.setRejectionReason(MapEventParticipantEntity.REJECTED.equals(normalised) ? trimmedReason : null);
        participantRepository.save(participant);

        // A car turned away after it was in the line-up leaves every contest ballot it was on.
        if (wasAccepted && MapEventParticipantEntity.REJECTED.equals(normalised)) {
            contestEntryRepository.deleteByEventIdAndCarIds(eventId, List.of(carId));
        }

        // An organizer deciding on their own car does not need telling.
        if (!participant.getOwnerId().equals(currentUserId)) {
            events.publishEvent(new MapEventCarDecidedEvent(
                    eventId,
                    event.getTitle(),
                    carId,
                    MapEventParticipantEntity.ACCEPTED.equals(normalised),
                    participant.getRejectionReason(),
                    participant.getOwnerId()));
        }

        return reloadAndAssemble(eventId, currentUserId);
    }

    // ==================================================================
    // Withdrawal requests
    // ==================================================================

    @Override
    @Transactional
    public MapEventDto requestWithdrawal(UUID currentUserId, UUID eventId, String note) {
        MapEventEntity event = loadApprovedEvent(eventId);
        if (event.hasFinished(Instant.now())) {
            throw new EventClosedException("This event has finished; withdrawal is closed");
        }

        List<MapEventParticipantEntity> rows = participantRepository.findByIdEventIdAndOwnerIdAndStatus(
                eventId, currentUserId, MapEventParticipantEntity.ACCEPTED);
        if (rows.isEmpty()) {
            throw new InvalidMapEventException(
                    "You have no accepted registration to withdraw from this event");
        }

        String trimmedNote = (note == null || note.isBlank()) ? null : note.trim();
        for (MapEventParticipantEntity row : rows) {
            row.setStatus(MapEventParticipantEntity.WITHDRAWN);
            row.setWithdrawNote(trimmedNote);
        }
        participantRepository.saveAll(rows);

        List<UUID> organizers = individualOrganizerIds(eventId).stream()
                .filter(organizerId -> !organizerId.equals(currentUserId))
                .toList();
        if (!organizers.isEmpty()) {
            events.publishEvent(new MapEventWithdrawalRequestedEvent(
                    eventId, event.getTitle(), currentUserId, trimmedNote, organizers));
        }

        return reloadAndAssemble(eventId, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MapEventWithdrawalRequestDto> listWithdrawalRequests(UUID currentUserId, UUID eventId) {
        MapEventEntity event = loadEvent(eventId);
        requireOrganizer(event, currentUserId);

        List<MapEventParticipantEntity> rows = participantRepository.findByIdEventIdAndStatus(
                eventId, MapEventParticipantEntity.WITHDRAWN);
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<UUID, CarSummaryDto> cars = resolveCars(
                rows.stream().map(row -> row.getId().getCarId()).toList());
        Map<UUID, ProfileSearchResultDto> owners = resolveProfiles(
                rows.stream().map(MapEventParticipantEntity::getOwnerId).toList());

        Map<UUID, List<MapEventParticipantEntity>> byOwner = rows.stream()
                .collect(Collectors.groupingBy(
                        MapEventParticipantEntity::getOwnerId, LinkedHashMap::new, Collectors.toList()));

        List<MapEventWithdrawalRequestDto> requests = new ArrayList<>(byOwner.size());
        for (Map.Entry<UUID, List<MapEventParticipantEntity>> entry : byOwner.entrySet()) {
            ProfileSearchResultDto owner = owners.get(entry.getKey());
            // A profile that no longer resolves is dropped rather than rendered as a blank row.
            if (owner == null) {
                continue;
            }
            List<CarSummaryDto> ownerCars = entry.getValue().stream()
                    .map(row -> cars.get(row.getId().getCarId()))
                    .filter(Objects::nonNull)
                    .toList();
            // Every row for one owner was set together in requestWithdrawal, so they share a note.
            String note = entry.getValue().get(0).getWithdrawNote();
            requests.add(new MapEventWithdrawalRequestDto(owner, ownerCars, note));
        }
        return requests;
    }

    @Override
    @Transactional
    public MapEventDto approveWithdrawal(UUID currentUserId, UUID eventId, UUID ownerId) {
        MapEventEntity event = loadEvent(eventId);
        requireOrganizer(event, currentUserId);

        List<MapEventParticipantEntity> rows = withdrawalRequestOrThrow(eventId, ownerId);
        // Leaving the meet means leaving its contests too — the withdraw dialog says as much.
        contestEntryRepository.deleteByEventIdAndCarIds(eventId, rows.stream().map(r -> r.getId().getCarId()).toList());
        participantRepository.deleteAll(rows);

        if (!ownerId.equals(currentUserId)) {
            events.publishEvent(new MapEventWithdrawalDecidedEvent(eventId, event.getTitle(), true, ownerId));
        }

        return reloadAndAssemble(eventId, currentUserId);
    }

    @Override
    @Transactional
    public MapEventDto rejectWithdrawal(UUID currentUserId, UUID eventId, UUID ownerId) {
        MapEventEntity event = loadEvent(eventId);
        requireOrganizer(event, currentUserId);

        List<MapEventParticipantEntity> rows = withdrawalRequestOrThrow(eventId, ownerId);
        // withdraw_note is left as-is: a historical record of the last attempt, overwritten only
        // by the owner's next withdrawal request.
        for (MapEventParticipantEntity row : rows) {
            row.setStatus(MapEventParticipantEntity.ACCEPTED);
        }
        participantRepository.saveAll(rows);

        if (!ownerId.equals(currentUserId)) {
            events.publishEvent(new MapEventWithdrawalDecidedEvent(eventId, event.getTitle(), false, ownerId));
        }

        return reloadAndAssemble(eventId, currentUserId);
    }

    private List<MapEventParticipantEntity> withdrawalRequestOrThrow(UUID eventId, UUID ownerId) {
        List<MapEventParticipantEntity> rows = participantRepository.findByIdEventIdAndOwnerIdAndStatus(
                eventId, ownerId, MapEventParticipantEntity.WITHDRAWN);
        if (rows.isEmpty()) {
            throw new InvalidMapEventException("No pending withdrawal request from that participant");
        }
        return rows;
    }

    private List<MapEventParticipantDto> toParticipantDtos(List<MapEventParticipantEntity> rows) {
        Map<UUID, CarSummaryDto> cars = resolveCars(rows.stream().map(row -> row.getId().getCarId()).toList());
        return rows.stream()
                .map(row -> new MapEventParticipantDto(
                        cars.get(row.getId().getCarId()), row.getStatus(), row.getCreatedAt(), row.getRejectionReason()))
                .filter(dto -> dto.car() != null)
                .toList();
    }

    // ==================================================================
    // Admin
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public MapEventPageDto<MapEventSummaryDto> listEventsForReview(String approvalStatus, String cursor, int size) {
        String normalised = normaliseApprovalStatus(approvalStatus);
        int pageSize = clampPageSize(size);
        MapEventCursor decoded = MapEventCursor.decode(cursor);

        List<MapEventEntity> rows = eventRepository.findByApprovalStatus(
                normalised,
                decoded == null ? null : decoded.timestamp(),
                decoded == null ? null : decoded.id(),
                Limit.of(pageSize + 1));

        return toSummaryPage(rows, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPendingReview() {
        return eventRepository.countByApprovalStatus(MapEventEntity.APPROVAL_PENDING);
    }

    @Override
    @Transactional
    public MapEventDto approveEvent(UUID eventId) {
        MapEventEntity event = loadEvent(eventId);
        event.setApprovalStatus(MapEventEntity.APPROVAL_ACCEPTED);
        event.setRejectionReason(null);
        eventRepository.save(event);

        events.publishEvent(new MapEventApprovedEvent(
                event.getId(), event.getTitle(), event.getCreatedBy()));
        return assemble(event, null, true);
    }

    @Override
    @Transactional
    public MapEventDto rejectEvent(UUID eventId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidMapEventException("A rejection reason is required");
        }
        MapEventEntity event = loadEvent(eventId);
        event.setApprovalStatus(MapEventEntity.APPROVAL_REJECTED);
        event.setRejectionReason(reason.trim());
        eventRepository.save(event);

        events.publishEvent(new MapEventRejectedEvent(
                event.getId(), event.getTitle(), event.getRejectionReason(), event.getCreatedBy()));
        return assemble(event, null, true);
    }

    @Override
    @Transactional
    public void deleteEventAsAdmin(UUID eventId) {
        purge(loadEvent(eventId));
    }

    @Override
    @Transactional(readOnly = true)
    public CarMeetDetailsDto getCarMeetDetails(UUID eventId) {
        return carMeetRepository.findById(eventId)
                .map(meet -> new CarMeetDetailsDto(meet.getRegistrationDeadline()))
                .orElse(null);
    }

    // ==================================================================
    // Assembly
    // ==================================================================

    /**
     * Builds the full event page. {@code viewerIsOrganizer} is passed in rather than recomputed
     * because every caller has already established it — and for admin reads there is no viewer at
     * all, yet the organizer's view (including the rejection reason) is what a reviewer needs.
     */
    private MapEventDto assemble(MapEventEntity event, UUID viewerId, boolean viewerIsOrganizer) {
        List<MapEventOrganizerDto> organizers = resolveOrganizers(event.getId());
        List<MapEventRuleDto> rules = resolveRules(event.getId());
        CarMeetDetailsDto carMeet = MapEventCategoryEntity.CAR_MEET.equals(event.getCategory().getId())
                ? getCarMeetDetails(event.getId())
                : null;

        return new MapEventDto(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getCategory().getId(),
                event.getCategory().getLabel(),
                event.getLocationName(),
                GeoSupport.latOf(event.getLocation()),
                GeoSupport.lngOf(event.getLocation()),
                event.getStartsAt(),
                event.getEndsAt(),
                resolveCoverUrl(event.getCoverImageKey()),
                event.getStatus(),
                event.getApprovalStatus(),
                // Why an event was turned down is between the admins and the people running it.
                viewerIsOrganizer ? event.getRejectionReason() : null,
                event.isRequiresParticipantApproval(),
                event.getMaxParticipantCapacity(),
                event.getAttendeesCount(),
                event.getAttendingCarsCount(),
                organizers,
                rules,
                carMeet,
                viewerState(event, viewerId, viewerIsOrganizer, carMeet),
                event.getCreatedAt());
    }

    private MapEventViewerStateDto viewerState(MapEventEntity event, UUID viewerId,
                                               boolean isOrganizer, CarMeetDetailsDto carMeet) {
        if (viewerId == null) {
            // Admin read: there is no app user on this request, so nothing is actionable.
            return new MapEventViewerStateDto(false, false, false, null, false, false, List.of());
        }

        Instant now = Instant.now();
        boolean finished = event.hasFinished(now);

        String attendance = attendeeRepository
                .findById(new MapEventAttendeeId(viewerId, event.getId()))
                .map(MapEventAttendeeEntity::getStatus)
                .orElse(null);

        List<UUID> myCars = participantRepository.findByIdEventIdAndOwnerId(event.getId(), viewerId)
                .stream()
                .map(row -> row.getId().getCarId())
                .toList();

        boolean deadlinePassed = carMeet != null && !now.isBefore(carMeet.registrationDeadline());

        return new MapEventViewerStateDto(
                event.getCreatedBy().equals(viewerId),
                isOrganizer,
                isOrganizer && event.isEditable(),
                attendance,
                event.isApproved() && !finished,
                event.isApproved() && !finished && !deadlinePassed,
                myCars);
    }

    /**
     * Resolves an event's organizer rows into display credits. Individuals and businesses are each
     * looked up in one batch, so a roster of any size costs two queries.
     */
    private List<MapEventOrganizerDto> resolveOrganizers(UUID eventId) {
        List<MapEventOrganizerEntity> rows = organizerRepository.findByEventIdOrderByRoleAscCreatedAtAsc(eventId);
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<UUID, ProfileSearchResultDto> profiles = resolveProfiles(rows.stream()
                .map(MapEventOrganizerEntity::getIndividualOrganizerId)
                .filter(Objects::nonNull)
                .toList());

        List<UUID> businessIds = rows.stream()
                .map(MapEventOrganizerEntity::getBusinessOrganizerId)
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, BusinessRefDto> businesses = businessIds.isEmpty()
                ? Map.of()
                : businessService.findBusinessRefsByIds(businessIds).stream()
                        .collect(Collectors.toMap(BusinessRefDto::id, Function.identity(), (a, b) -> a));

        List<MapEventOrganizerDto> credits = new ArrayList<>(rows.size());
        for (MapEventOrganizerEntity row : rows) {
            if (row.getIndividualOrganizerId() != null) {
                ProfileSearchResultDto profile = profiles.get(row.getIndividualOrganizerId());
                credits.add(new MapEventOrganizerDto(
                        row.getId(),
                        MapEventOrganizerDto.INDIVIDUAL,
                        row.getRole(),
                        row.getIndividualOrganizerId(),
                        profile == null ? null : profile.name(),
                        profile == null ? null : profile.username(),
                        profile == null ? null : profile.avatarUrl()));
            } else {
                // A business that is no longer visible keeps its slot but renders nameless, rather
                // than silently vanishing from the credits.
                BusinessRefDto business = businesses.get(row.getBusinessOrganizerId());
                credits.add(new MapEventOrganizerDto(
                        row.getId(),
                        MapEventOrganizerDto.BUSINESS,
                        row.getRole(),
                        row.getBusinessOrganizerId(),
                        business == null ? null : business.name(),
                        null,
                        business == null ? null : business.logoUrl()));
            }
        }
        return credits;
    }

    private List<MapEventRuleDto> resolveRules(UUID eventId) {
        return ruleRepository.findByEventIdOrderBySortOrderAsc(eventId)
                .stream()
                .map(row -> new MapEventRuleDto(row.getId(), row.getRule(), row.getSortOrder()))
                .toList();
    }

    private MapEventPageDto<MapEventSummaryDto> toSummaryPage(List<MapEventEntity> rows, int pageSize) {
        boolean hasMore = rows.size() > pageSize;
        List<MapEventEntity> page = hasMore ? rows.subList(0, pageSize) : rows;

        Map<UUID, ProfileSearchResultDto> creators = resolveProfiles(
                page.stream().map(MapEventEntity::getCreatedBy).toList());

        List<MapEventSummaryDto> items = page.stream()
                .map(event -> new MapEventSummaryDto(
                        event.getId(),
                        event.getTitle(),
                        event.getCategory().getId(),
                        event.getCategory().getLabel(),
                        event.getLocationName(),
                        GeoSupport.latOf(event.getLocation()),
                        GeoSupport.lngOf(event.getLocation()),
                        event.getStartsAt(),
                        event.getEndsAt(),
                        resolveCoverUrl(event.getCoverImageKey()),
                        event.getStatus(),
                        event.getApprovalStatus(),
                        event.getRejectionReason(),
                        event.getMaxParticipantCapacity(),
                        event.getAttendeesCount(),
                        event.getAttendingCarsCount(),
                        creators.get(event.getCreatedBy()),
                        event.getCreatedAt()))
                .toList();

        return new MapEventPageDto<>(items,
                nextCursor(hasMore, page, MapEventEntity::getCreatedAt, MapEventEntity::getId));
    }

    private MapEventDto reloadAndAssemble(UUID eventId, UUID viewerId) {
        // Counts are trigger-owned, so the row must be re-read after an RSVP change for the
        // response to show the new total rather than the stale one held in the session.
        entityManager.flush();
        MapEventEntity event = loadEvent(eventId);
        entityManager.refresh(event);
        return assemble(event, viewerId, isOrganizer(eventId, viewerId));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private MapEventEntity loadEvent(UUID eventId) {
        return eventRepository.findWithCategoryById(eventId)
                .orElseThrow(() -> new MapEventNotFoundException(eventId));
    }

    /** Loads an event that must be approved — the state required to take part in it. */
    private MapEventEntity loadApprovedEvent(UUID eventId) {
        MapEventEntity event = loadEvent(eventId);
        if (!event.isApproved()) {
            throw new MapEventNotFoundException(eventId);
        }
        return event;
    }

    private void requireVisible(UUID currentUserId, UUID eventId) {
        requireVisible(currentUserId, loadEvent(eventId));
    }

    private void requireVisible(UUID currentUserId, MapEventEntity event) {
        if (!event.isApproved() && !isOrganizer(event.getId(), currentUserId)) {
            throw new MapEventNotFoundException(event.getId());
        }
    }

    private boolean isOrganizer(UUID eventId, UUID userId) {
        return userId != null
                && organizerRepository.existsByEventIdAndIndividualOrganizerId(eventId, userId);
    }

    /** The organizers who can actually be notified — businesses have no login, so no inbox. */
    private List<UUID> individualOrganizerIds(UUID eventId) {
        return organizerRepository.findByEventIdOrderByRoleAscCreatedAtAsc(eventId)
                .stream()
                .map(MapEventOrganizerEntity::getIndividualOrganizerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private void requireOrganizer(MapEventEntity event, UUID userId) {
        if (!isOrganizer(event.getId(), userId)) {
            throw new NotEventOrganizerException("You do not organize this event");
        }
    }

    private void requireCreator(MapEventEntity event, UUID userId) {
        if (!event.getCreatedBy().equals(userId)) {
            throw new NotEventOrganizerException("Only the event's creator can manage organizers");
        }
    }

    private void requireEditable(MapEventEntity event) {
        if (!event.isEditable()) {
            throw new EventNotEditableException(
                    "An approved event can no longer be edited; cancel it instead");
        }
        if (MapEventEntity.STATUS_CANCELED.equals(event.getStatus())
                || MapEventEntity.STATUS_PREVIOUS.equals(event.getStatus())) {
            throw new EventNotEditableException("A cancelled or finished event can no longer be edited");
        }
    }

    /** Deletes an event and its cover object. Child rows cascade in the database. */
    private void purge(MapEventEntity event) {
        String coverKey = event.getCoverImageKey();
        UUID eventId = event.getId();
        eventRepository.delete(event);
        if (coverKey != null) {
            deleteR2ObjectsAfterCommit(List.of(coverKey), eventId);
        }
    }

    private void saveCarMeetDetails(MapEventEntity event, Instant registrationDeadline, boolean required) {
        if (registrationDeadline == null) {
            if (required) {
                throw new InvalidMapEventException("A car meet needs a registration deadline");
            }
            return;
        }
        if (registrationDeadline.isAfter(event.getStartsAt())) {
            throw new InvalidMapEventException(
                    "The registration deadline cannot be after the event starts");
        }
        CarMeetEntity meet = carMeetRepository.findById(event.getId()).orElseGet(() -> {
            CarMeetEntity fresh = new CarMeetEntity();
            fresh.setEventId(event.getId());
            return fresh;
        });
        meet.setRegistrationDeadline(registrationDeadline);
        carMeetRepository.save(meet);
    }

    private Map<UUID, ProfileSearchResultDto> resolveProfiles(List<UUID> ids) {
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return profileService.findByIds(distinct).stream()
                .collect(Collectors.toMap(ProfileSearchResultDto::id, Function.identity(), (a, b) -> a));
    }

    private Map<UUID, CarSummaryDto> resolveCars(List<UUID> ids) {
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return garageService.findCarsByIds(distinct).stream()
                .collect(Collectors.toMap(CarSummaryDto::id, Function.identity(), (a, b) -> a));
    }

    private String resolveCoverUrl(String storedKey) {
        if (storedKey == null || storedKey.isBlank() || storedKey.startsWith("http")) {
            return storedKey;
        }
        return storageService.publicUrl(StorageBucket.MAP_EVENTS, storedKey);
    }

    /**
     * Builds the token for the next page, or {@code null} when this was the last one. Keyed on the
     * same {@code (timestamp, id)} pair the query orders by, so the two can never disagree.
     */
    private <T> String nextCursor(boolean hasMore, List<T> page,
                                  Function<T, Instant> timestamp, Function<T, UUID> id) {
        if (!hasMore || page.isEmpty()) {
            return null;
        }
        T last = page.get(page.size() - 1);
        return new MapEventCursor(timestamp.apply(last), id.apply(last)).encode();
    }

    private void validateSearchArea(double lat, double lng, double radiusKm, int limit) {
        if (Double.isNaN(lat) || Double.isNaN(lng) || Double.isNaN(radiusKm)
                || Double.isInfinite(lat) || Double.isInfinite(lng) || Double.isInfinite(radiusKm)) {
            throw new InvalidSearchAreaException("Coordinates and radius must be finite numbers");
        }
        if (lat < -90 || lat > 90) {
            throw new InvalidSearchAreaException("lat must be between -90 and 90");
        }
        if (lng < -180 || lng > 180) {
            throw new InvalidSearchAreaException("lng must be between -180 and 180");
        }
        if (radiusKm <= 0 || radiusKm > MAX_RADIUS_KM) {
            throw new InvalidSearchAreaException("radius_km must be between 0 and " + MAX_RADIUS_KM);
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidSearchAreaException("limit must be between 1 and " + MAX_LIMIT);
        }
    }

    private void validateCoordinates(Double lat, Double lng) {
        if (lat == null || lng == null
                || lat.isNaN() || lng.isNaN() || lat.isInfinite() || lng.isInfinite()
                || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new InvalidMapEventException("lat must be within -90..90 and lng within -180..180");
        }
    }

    /** Like {@link #validateCoordinates}, but for an optional hint: invalid input is dropped, not an error. */
    private static Double sanitizeCoordinate(Double value, double min, double max) {
        if (value == null || value.isNaN() || value.isInfinite() || value < min || value > max) {
            return null;
        }
        return value;
    }

    private void validateTimings(Instant startsAt, Instant endsAt, Instant now) {
        if (!startsAt.isAfter(now)) {
            throw new InvalidMapEventException("The event must start in the future");
        }
        if (endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new InvalidMapEventException("The event must end after it starts");
        }
    }

    /**
     * @param nullable whether a missing value means "no filter" (list reads) or is an error
     *                 (writing an RSVP)
     */
    private String normaliseAttendanceStatus(String status, boolean nullable) {
        if (status == null || status.isBlank()) {
            if (nullable) {
                return null;
            }
            throw new InvalidMapEventException("An attendance status is required");
        }
        String trimmed = status.trim().toLowerCase();
        if (!MapEventAttendeeEntity.ATTENDING.equals(trimmed)
                && !MapEventAttendeeEntity.INTERESTED.equals(trimmed)) {
            throw new InvalidMapEventException(
                    "Attendance status must be 'attending' or 'interested'");
        }
        return trimmed;
    }

    /** {@code null} means "no filter", resolved by the caller to the public line-up. */
    private String normaliseParticipantStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String trimmed = status.trim().toLowerCase();
        if (!Set.of(MapEventParticipantEntity.PENDING,
                    MapEventParticipantEntity.ACCEPTED,
                    MapEventParticipantEntity.REJECTED,
                    MapEventParticipantEntity.WITHDRAWN).contains(trimmed)) {
            throw new InvalidMapEventException(
                    "Participant status must be 'pending', 'accepted', 'rejected' or 'withdrawn'");
        }
        return trimmed;
    }

    private String normaliseDecision(String status) {
        String trimmed = status == null ? "" : status.trim().toLowerCase();
        if (!MapEventParticipantEntity.ACCEPTED.equals(trimmed)
                && !MapEventParticipantEntity.REJECTED.equals(trimmed)) {
            throw new InvalidMapEventException("A decision must be 'accepted' or 'rejected'");
        }
        return trimmed;
    }

    private String normaliseApprovalStatus(String status) {
        String trimmed = (status == null || status.isBlank())
                ? MapEventEntity.APPROVAL_PENDING
                : status.trim().toLowerCase();
        if (!Set.of(MapEventEntity.APPROVAL_PENDING,
                    MapEventEntity.APPROVAL_ACCEPTED,
                    MapEventEntity.APPROVAL_REJECTED).contains(trimmed)) {
            throw new InvalidMapEventException(
                    "Approval status must be 'pending', 'accepted' or 'rejected'");
        }
        return trimmed;
    }

    private int clampPageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * Registers an after-commit callback that deletes the given objects from R2. Deletion runs only
     * if the surrounding DB transaction commits; if R2 deletion then fails, the database is already
     * consistent and we just log the orphaned keys.
     */
    private void deleteR2ObjectsAfterCommit(List<String> keys, UUID eventId) {
        if (keys.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storageService.deleteByKeys(StorageBucket.MAP_EVENTS, keys);
                } catch (Exception e) {
                    log.warn("DB committed but failed to delete {} orphaned R2 objects for event {}",
                            keys.size(), eventId, e);
                }
            }
        });
    }
}
