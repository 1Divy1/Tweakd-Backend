package com.tweakdapp.backend.forums.internal;

import com.tweakdapp.backend.forums.dto.request.CreateReplyRequest;
import com.tweakdapp.backend.forums.dto.request.CreateThreadRequest;
import com.tweakdapp.backend.forums.dto.request.UpdateThreadRequest;
import com.tweakdapp.backend.forums.events.ForumReplyTaggedEvent;
import com.tweakdapp.backend.forums.events.ForumThreadTaggedEvent;
import com.tweakdapp.backend.forums.exception.CarOwnerNotTaggedException;
import com.tweakdapp.backend.forums.exception.ForumPostNotFoundException;
import com.tweakdapp.backend.forums.exception.InvalidReferenceException;
import com.tweakdapp.backend.forums.exception.ThreadNotFoundException;
import com.tweakdapp.backend.forums.internal.entities.ForumThreadEntity;
import com.tweakdapp.backend.forums.internal.entities.ForumThreadReplyEntity;
import com.tweakdapp.backend.forums.internal.entities.ForumThreadTaggedCarEntity;
import com.tweakdapp.backend.forums.internal.entities.ForumThreadTaggedCarId;
import com.tweakdapp.backend.forums.internal.entities.ForumThreadTaggedPersonEntity;
import com.tweakdapp.backend.forums.internal.entities.ForumThreadTaggedPersonId;
import com.tweakdapp.backend.forums.internal.repositories.ForumPostLikeRepository;
import com.tweakdapp.backend.forums.internal.repositories.ForumPostRepository;
import com.tweakdapp.backend.forums.internal.repositories.ForumReplyTaggedCarRepository;
import com.tweakdapp.backend.forums.internal.repositories.ForumReplyTaggedPersonRepository;
import com.tweakdapp.backend.forums.internal.repositories.ForumShortcutRepository;
import com.tweakdapp.backend.forums.internal.repositories.ForumThreadLikeRepository;
import com.tweakdapp.backend.forums.internal.repositories.ForumThreadReadRepository;
import com.tweakdapp.backend.forums.internal.repositories.ForumThreadRepository;
import com.tweakdapp.backend.forums.internal.repositories.ForumThreadSaveRepository;
import com.tweakdapp.backend.forums.internal.repositories.ForumThreadTaggedCarRepository;
import com.tweakdapp.backend.forums.internal.repositories.ForumThreadTaggedPersonRepository;
import com.tweakdapp.backend.forums.internal.repositories.ForumThreadTopicOptionsRepository;
import com.tweakdapp.backend.forums.internal.repositories.ForumThreadTopicRepository;
import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import com.tweakdapp.backend.report.ReportService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the forums tagging rules in {@link ForumsServiceImpl}: a car may only be tagged
 * when its owner is tagged too (the author's own cars excepted), tag rows are written on create,
 * PATCH tag lists are replace-all with {@code null} meaning "leave alone", and only <em>newly</em>
 * tagged users are notified. Only the collaborators each path actually touches are stubbed.
 */
class ForumsTaggingTest {

    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final UUID THREAD = UUID.fromString("00000000-0000-0000-0000-000000000b01");
    private static final UUID REPLY = UUID.fromString("00000000-0000-0000-0000-000000000b02");
    private static final UUID OWNER_CAR = UUID.fromString("00000000-0000-0000-0000-000000000c01");
    private static final UUID AUTHOR_CAR = UUID.fromString("00000000-0000-0000-0000-000000000c02");

    private ForumThreadRepository threadRepository;
    private ForumPostRepository postRepository;
    private ForumThreadTaggedPersonRepository threadTaggedPersonRepository;
    private ForumThreadTaggedCarRepository threadTaggedCarRepository;
    private ForumReplyTaggedPersonRepository replyTaggedPersonRepository;
    private ForumReplyTaggedCarRepository replyTaggedCarRepository;
    private ProfileService profileService;
    private GarageService garageService;
    private ApplicationEventPublisher eventPublisher;
    private ForumsServiceImpl service;

    @BeforeEach
    void setUp() {
        threadRepository = mock(ForumThreadRepository.class);
        postRepository = mock(ForumPostRepository.class);
        threadTaggedPersonRepository = mock(ForumThreadTaggedPersonRepository.class);
        threadTaggedCarRepository = mock(ForumThreadTaggedCarRepository.class);
        replyTaggedPersonRepository = mock(ForumReplyTaggedPersonRepository.class);
        replyTaggedCarRepository = mock(ForumReplyTaggedCarRepository.class);
        profileService = mock(ProfileService.class);
        garageService = mock(GarageService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        service = new ForumsServiceImpl(
                mock(ForumThreadTopicOptionsRepository.class),
                threadRepository,
                postRepository,
                mock(ForumThreadTopicRepository.class),
                mock(ForumThreadLikeRepository.class),
                mock(ForumPostLikeRepository.class),
                mock(ForumThreadSaveRepository.class),
                mock(ForumThreadReadRepository.class),
                threadTaggedPersonRepository,
                threadTaggedCarRepository,
                replyTaggedPersonRepository,
                replyTaggedCarRepository,
                mock(ForumShortcutRepository.class),
                profileService,
                garageService,
                mock(ReportService.class),
                eventPublisher);
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));

        // The thread re-read that every write ends with (matched by any() because createThread
        // generates the id itself); the assembly lookups it makes all default to empty through
        // Mockito, which is enough for these tag-rule assertions.
        when(threadRepository.findById(any())).thenReturn(Optional.of(thread()));
    }

    private ForumThreadEntity thread() {
        ForumThreadEntity t = new ForumThreadEntity();
        t.setId(THREAD);
        t.setUserId(AUTHOR);
        t.setTitle("title");
        t.setDeleted(false);
        t.setLocked(false);
        t.setCreatedAt(Instant.now());
        return t;
    }

    private ForumThreadReplyEntity reply() {
        ForumThreadReplyEntity r = new ForumThreadReplyEntity();
        r.setId(REPLY);
        r.setThreadId(THREAD);
        r.setUserId(AUTHOR);
        r.setContent("body");
        r.setDeleted(false);
        r.setCreatedAt(Instant.now());
        return r;
    }

    /** Stubs the profile-existence check for the given tagged people. */
    private void profilesExist(UUID... ids) {
        List<ProfileSearchResultDto> found = java.util.Arrays.stream(ids)
                .map(id -> new ProfileSearchResultDto(id, "User " + id, "user-" + id, null))
                .toList();
        when(profileService.findByIds(anyCollection())).thenAnswer(invocation -> {
            Collection<?> requested = invocation.getArgument(0);
            return found.stream().filter(p -> requested.contains(p.id())).toList();
        });
    }

    // ---- the "tag the owner first" rule -------------------------------------

    @Test
    void createThreadRejectsACarWhoseOwnerIsNotTagged() {
        profilesExist(OTHER);
        when(garageService.findCarOwnerIds(List.of(OWNER_CAR))).thenReturn(Map.of(OWNER_CAR, OWNER));

        CreateThreadRequest request = new CreateThreadRequest(
                "title", "body", null, null, null, List.of(OTHER), List.of(OWNER_CAR));

        assertThatThrownBy(() -> service.createThread(AUTHOR.toString(), request))
                .isInstanceOf(CarOwnerNotTaggedException.class);
        verify(threadRepository, never()).save(any());
    }

    @Test
    void createThreadAcceptsACarWhoseOwnerIsTagged() {
        profilesExist(OWNER);
        when(garageService.findCarOwnerIds(List.of(OWNER_CAR))).thenReturn(Map.of(OWNER_CAR, OWNER));

        service.createThread(AUTHOR.toString(), new CreateThreadRequest(
                "title", "body", null, null, null, List.of(OWNER), List.of(OWNER_CAR)));

        verify(threadTaggedPersonRepository).save(any(ForumThreadTaggedPersonEntity.class));
        verify(threadTaggedCarRepository).save(any(ForumThreadTaggedCarEntity.class));
    }

    @Test
    void createThreadAcceptsTheAuthorsOwnCarWithoutASelfTag() {
        when(garageService.findCarOwnerIds(List.of(AUTHOR_CAR))).thenReturn(Map.of(AUTHOR_CAR, AUTHOR));

        service.createThread(AUTHOR.toString(), new CreateThreadRequest(
                "title", "body", null, null, null, null, List.of(AUTHOR_CAR)));

        verify(threadTaggedCarRepository).save(any(ForumThreadTaggedCarEntity.class));
        verify(threadTaggedPersonRepository, never()).save(any());
    }

    @Test
    void createThreadRejectsATaggedPersonWhoDoesNotExist() {
        profilesExist(); // nothing resolves

        CreateThreadRequest request = new CreateThreadRequest(
                "title", "body", null, null, null, List.of(OTHER), null);

        assertThatThrownBy(() -> service.createThread(AUTHOR.toString(), request))
                .isInstanceOf(InvalidReferenceException.class);
    }

    // ---- notifications ------------------------------------------------------

    @Test
    void createThreadNotifiesEveryTaggedPersonAndFlagsTheCarOwner() {
        profilesExist(OWNER, OTHER);
        when(garageService.findCarOwnerIds(List.of(OWNER_CAR))).thenReturn(Map.of(OWNER_CAR, OWNER));

        service.createThread(AUTHOR.toString(), new CreateThreadRequest(
                "title", "body", null, null, null, List.of(OWNER, OTHER), List.of(OWNER_CAR)));

        ArgumentCaptor<ForumThreadTaggedEvent> captor = ArgumentCaptor.forClass(ForumThreadTaggedEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ForumThreadTaggedEvent::recipientId, ForumThreadTaggedEvent::carTagged)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(OWNER, true),
                        org.assertj.core.groups.Tuple.tuple(OTHER, false));
    }

    @Test
    void tagsDoNotSelfNotify() {
        profilesExist(AUTHOR);

        service.createThread(AUTHOR.toString(), new CreateThreadRequest(
                "title", "body", null, null, null, List.of(AUTHOR), null));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void addReplyNotifiesTaggedPeopleWithTheThreadAndReplyIds() {
        profilesExist(OTHER);
        when(postRepository.findById(any())).thenReturn(Optional.of(reply()));

        service.addReply(AUTHOR.toString(), THREAD,
                new CreateReplyRequest("look at this", null, List.of(OTHER), null));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOfSatisfying(ForumReplyTaggedEvent.class, event -> {
            assertThat(event.threadId()).isEqualTo(THREAD);
            assertThat(event.recipientId()).isEqualTo(OTHER);
            assertThat(event.actorId()).isEqualTo(AUTHOR);
            assertThat(event.carTagged()).isFalse();
        });
    }

    // ---- PATCH semantics ----------------------------------------------------

    @Test
    void updateThreadLeavesTagsAloneWhenTheListsAreNull() {
        when(threadTaggedPersonRepository.findAllByIdThreadId(THREAD))
                .thenReturn(List.of(taggedPerson(OTHER)));

        service.updateThread(AUTHOR.toString(), THREAD, new UpdateThreadRequest("edited", null, null));

        verify(threadTaggedPersonRepository, never()).deleteAllByIdThreadId(any());
        verify(threadTaggedCarRepository, never()).deleteAllByIdThreadId(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void updateThreadReplacesTheTagSetAndOnlyNotifiesTheNewcomers() {
        profilesExist(OWNER, OTHER);
        when(threadTaggedPersonRepository.findAllByIdThreadId(THREAD))
                .thenReturn(List.of(taggedPerson(OTHER)));

        service.updateThread(AUTHOR.toString(), THREAD,
                new UpdateThreadRequest("edited", List.of(OTHER, OWNER), null));

        verify(threadTaggedPersonRepository).deleteAllByIdThreadId(THREAD);
        ArgumentCaptor<ForumThreadTaggedEvent> captor = ArgumentCaptor.forClass(ForumThreadTaggedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        assertThat(captor.getValue().recipientId()).isEqualTo(OWNER); // OTHER was already tagged
    }

    @Test
    void updateThreadRejectsUntaggingAPersonWhoseCarStaysTagged() {
        profilesExist(OTHER);
        when(threadTaggedPersonRepository.findAllByIdThreadId(THREAD))
                .thenReturn(List.of(taggedPerson(OWNER)));
        when(threadTaggedCarRepository.findAllByIdThreadId(THREAD))
                .thenReturn(List.of(taggedCar(OWNER_CAR)));
        when(garageService.findCarOwnerIds(List.of(OWNER_CAR))).thenReturn(Map.of(OWNER_CAR, OWNER));

        assertThatThrownBy(() -> service.updateThread(AUTHOR.toString(), THREAD,
                new UpdateThreadRequest("edited", List.of(OTHER), null)))
                .isInstanceOf(CarOwnerNotTaggedException.class);
    }

    // ---- untagging yourself -------------------------------------------------

    @Test
    void untaggingYourselfFromAThreadTakesYourOwnTaggedCarsWithIt() {
        when(threadRepository.existsById(THREAD)).thenReturn(true);
        when(garageService.findCarIdsByOwner(OWNER)).thenReturn(List.of(OWNER_CAR));

        service.removeSelfTagsFromThread(OWNER, THREAD);

        // Leaving the car behind would break the "a tagged car's owner is tagged too" rule.
        verify(threadTaggedPersonRepository).deleteByIdThreadIdAndIdUserId(THREAD, OWNER);
        verify(threadTaggedCarRepository).deleteByIdThreadIdAndIdCarIdIn(THREAD, List.of(OWNER_CAR));
        verify(threadTaggedPersonRepository, never()).deleteAllByIdThreadId(any());
    }

    @Test
    void untaggingFromAThreadThatIsGoneIsNotFound() {
        when(threadRepository.existsById(THREAD)).thenReturn(false);

        assertThatThrownBy(() -> service.removeSelfTagsFromThread(OWNER, THREAD))
                .isInstanceOf(ThreadNotFoundException.class);
        verify(threadTaggedPersonRepository, never()).deleteByIdThreadIdAndIdUserId(any(), any());
    }

    @Test
    void untaggingYourselfFromAReplyTakesYourOwnTaggedCarsWithIt() {
        when(postRepository.existsById(REPLY)).thenReturn(true);
        when(garageService.findCarIdsByOwner(OWNER)).thenReturn(List.of(OWNER_CAR));

        service.removeSelfTagsFromReply(OWNER, REPLY);

        verify(replyTaggedPersonRepository).deleteByIdReplyIdAndIdUserId(REPLY, OWNER);
        verify(replyTaggedCarRepository).deleteByIdReplyIdAndIdCarIdIn(REPLY, List.of(OWNER_CAR));
    }

    @Test
    void untaggingFromAReplyThatIsGoneIsNotFound() {
        when(postRepository.existsById(REPLY)).thenReturn(false);

        assertThatThrownBy(() -> service.removeSelfTagsFromReply(OWNER, REPLY))
                .isInstanceOf(ForumPostNotFoundException.class);
        verify(replyTaggedPersonRepository, never()).deleteByIdReplyIdAndIdUserId(any(), any());
    }

    private static ForumThreadTaggedPersonEntity taggedPerson(UUID userId) {
        ForumThreadTaggedPersonEntity e = new ForumThreadTaggedPersonEntity();
        e.setId(new ForumThreadTaggedPersonId(THREAD, userId));
        return e;
    }

    private static ForumThreadTaggedCarEntity taggedCar(UUID carId) {
        ForumThreadTaggedCarEntity e = new ForumThreadTaggedCarEntity();
        e.setId(new ForumThreadTaggedCarId(THREAD, carId));
        return e;
    }
}
