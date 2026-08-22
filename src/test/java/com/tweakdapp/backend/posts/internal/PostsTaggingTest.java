package com.tweakdapp.backend.posts.internal;

import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.garage.dto.CarOwnerDto;
import com.tweakdapp.backend.garage.dto.CarSummaryDto;
import com.tweakdapp.backend.posts.PostCommentTaggedEvent;
import com.tweakdapp.backend.posts.PostTaggedEvent;
import com.tweakdapp.backend.posts.dto.CommentDto;
import com.tweakdapp.backend.posts.dto.CommentPageDto;
import com.tweakdapp.backend.posts.dto.request.CreateCommentRequest;
import com.tweakdapp.backend.posts.dto.request.CreatePostRequest;
import com.tweakdapp.backend.posts.dto.request.UpdatePostRequest;
import com.tweakdapp.backend.posts.exception.CarOwnerNotTaggedException;
import com.tweakdapp.backend.posts.exception.CommentNotFoundException;
import com.tweakdapp.backend.posts.exception.InvalidReferenceException;
import com.tweakdapp.backend.posts.exception.PostNotFoundException;
import com.tweakdapp.backend.posts.internal.entities.CommentEntity;
import com.tweakdapp.backend.posts.internal.entities.CommentTaggedCarEntity;
import com.tweakdapp.backend.posts.internal.entities.CommentTaggedCarId;
import com.tweakdapp.backend.posts.internal.entities.CommentTaggedPersonEntity;
import com.tweakdapp.backend.posts.internal.entities.CommentTaggedPersonId;
import com.tweakdapp.backend.posts.internal.entities.PostEntity;
import com.tweakdapp.backend.posts.internal.entities.TaggedPersonEntity;
import com.tweakdapp.backend.posts.internal.entities.TaggedPersonId;
import com.tweakdapp.backend.posts.internal.repositories.CommentLikeRepository;
import com.tweakdapp.backend.posts.internal.repositories.CommentRepository;
import com.tweakdapp.backend.posts.internal.repositories.CommentTaggedCarRepository;
import com.tweakdapp.backend.posts.internal.repositories.CommentTaggedPersonRepository;
import com.tweakdapp.backend.posts.internal.repositories.PostImageRepository;
import com.tweakdapp.backend.posts.internal.repositories.PostLikeRepository;
import com.tweakdapp.backend.posts.internal.repositories.PostRepository;
import com.tweakdapp.backend.posts.internal.repositories.PostShareRepository;
import com.tweakdapp.backend.posts.internal.repositories.SavedPostRepository;
import com.tweakdapp.backend.posts.internal.repositories.TaggedCarRepository;
import com.tweakdapp.backend.posts.internal.repositories.TaggedPersonRepository;
import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import com.tweakdapp.backend.report.ReportService;
import com.tweakdapp.backend.storage.StorageService;
import jakarta.persistence.EntityManager;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for tagging in the posts module: the "tag the owner first" rule applied to post
 * comments (the same rule posts and forum threads/replies already enforce), the tag rows written on
 * comment create, the tags echoed back on the create response and on a comment page, and the
 * {@code PostTaggedEvent} / {@code PostCommentTaggedEvent} notifications — only for newly tagged
 * users, never for a self-tag. Only the collaborators each path actually touches are stubbed.
 */
class PostsTaggingTest {

    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final UUID POST = UUID.fromString("00000000-0000-0000-0000-000000000b01");
    private static final UUID COMMENT = UUID.fromString("00000000-0000-0000-0000-000000000b02");
    private static final UUID DELETED_COMMENT = UUID.fromString("00000000-0000-0000-0000-000000000b03");
    private static final UUID OWNER_CAR = UUID.fromString("00000000-0000-0000-0000-000000000c01");
    private static final UUID AUTHOR_CAR = UUID.fromString("00000000-0000-0000-0000-000000000c02");

    private PostRepository postRepository;
    private CommentRepository commentRepository;
    private CommentTaggedPersonRepository commentTaggedPersonRepository;
    private CommentTaggedCarRepository commentTaggedCarRepository;
    private TaggedPersonRepository taggedPersonRepository;
    private TaggedCarRepository taggedCarRepository;
    private ProfileService profileService;
    private GarageService garageService;
    private ApplicationEventPublisher eventPublisher;
    private PostsServiceImpl service;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        commentRepository = mock(CommentRepository.class);
        commentTaggedPersonRepository = mock(CommentTaggedPersonRepository.class);
        commentTaggedCarRepository = mock(CommentTaggedCarRepository.class);
        taggedPersonRepository = mock(TaggedPersonRepository.class);
        taggedCarRepository = mock(TaggedCarRepository.class);
        profileService = mock(ProfileService.class);
        garageService = mock(GarageService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        service = new PostsServiceImpl(
                postRepository,
                mock(PostImageRepository.class),
                taggedPersonRepository,
                taggedCarRepository,
                commentRepository,
                mock(CommentLikeRepository.class),
                commentTaggedPersonRepository,
                commentTaggedCarRepository,
                mock(PostLikeRepository.class),
                mock(SavedPostRepository.class),
                mock(PostShareRepository.class),
                profileService,
                garageService,
                mock(StorageService.class),
                mock(ReportService.class),
                eventPublisher);
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));

        // The post / comment re-reads every write ends with (matched by any() because create
        // generates the id itself); the assembly lookups default to empty through Mockito, which is
        // enough for these tag assertions.
        when(postRepository.findById(any())).thenReturn(Optional.of(post()));
        when(commentRepository.findById(any())).thenReturn(Optional.of(comment(COMMENT, AUTHOR, false)));
    }

    private PostEntity post() {
        PostEntity p = new PostEntity();
        p.setId(POST);
        p.setUserId(AUTHOR);
        p.setDescription("caption");
        p.setLikesCount(0L);
        p.setCommentsCount(0L);
        p.setSharesCount(0L);
        p.setQuoteSharesCount(0L);
        p.setSavedCount(0L);
        p.setCreatedAt(Instant.now());
        return p;
    }

    private CommentEntity comment(UUID id, UUID userId, boolean deleted) {
        CommentEntity c = new CommentEntity();
        c.setId(id);
        c.setPostId(POST);
        c.setUserId(userId);
        c.setContent("nice");
        c.setDeleted(deleted);
        c.setCreatedAt(Instant.now());
        return c;
    }

    /** Stubs the profile-existence check for the given tagged people. */
    private void profilesExist(UUID... ids) {
        List<ProfileSearchResultDto> found = Arrays.stream(ids)
                .map(id -> new ProfileSearchResultDto(id, "User " + id, "user-" + id, null))
                .toList();
        when(profileService.findByIds(anyCollection())).thenAnswer(invocation -> {
            Collection<?> requested = invocation.getArgument(0);
            return found.stream().filter(p -> requested.contains(p.id())).toList();
        });
    }

    private static CarSummaryDto car(UUID carId, UUID ownerId) {
        return new CarSummaryDto(carId, "BMW", "M3", null, null, new CarOwnerDto(ownerId, "user-" + ownerId));
    }

    // ---- the "tag the owner first" rule on comments --------------------------

    @Test
    void addCommentRejectsACarWhoseOwnerIsNotTagged() {
        profilesExist(OTHER);
        when(garageService.findCarOwnerIds(List.of(OWNER_CAR))).thenReturn(Map.of(OWNER_CAR, OWNER));

        CreateCommentRequest request = new CreateCommentRequest("nice", null, List.of(OTHER), List.of(OWNER_CAR));

        assertThatThrownBy(() -> service.addComment(AUTHOR.toString(), POST, request))
                .isInstanceOf(CarOwnerNotTaggedException.class);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void addCommentAcceptsACarWhoseOwnerIsTagged() {
        profilesExist(OWNER);
        when(garageService.findCarOwnerIds(List.of(OWNER_CAR))).thenReturn(Map.of(OWNER_CAR, OWNER));

        service.addComment(AUTHOR.toString(), POST,
                new CreateCommentRequest("nice", null, List.of(OWNER), List.of(OWNER_CAR)));

        verify(commentTaggedPersonRepository).save(any(CommentTaggedPersonEntity.class));
        verify(commentTaggedCarRepository).save(any(CommentTaggedCarEntity.class));
    }

    @Test
    void addCommentAcceptsTheCommenterOwnCarWithoutASelfTag() {
        when(garageService.findCarOwnerIds(List.of(AUTHOR_CAR))).thenReturn(Map.of(AUTHOR_CAR, AUTHOR));

        service.addComment(AUTHOR.toString(), POST,
                new CreateCommentRequest("mine", null, null, List.of(AUTHOR_CAR)));

        verify(commentTaggedCarRepository).save(any(CommentTaggedCarEntity.class));
        verify(commentTaggedPersonRepository, never()).save(any());
    }

    @Test
    void addCommentRejectsATaggedPersonWhoDoesNotExist() {
        profilesExist(); // nothing resolves

        CreateCommentRequest request = new CreateCommentRequest("nice", null, List.of(OTHER), null);

        assertThatThrownBy(() -> service.addComment(AUTHOR.toString(), POST, request))
                .isInstanceOf(InvalidReferenceException.class);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void addCommentRejectsATaggedCarThatDoesNotExist() {
        profilesExist(OWNER);
        when(garageService.findCarOwnerIds(List.of(OWNER_CAR))).thenReturn(Map.of());

        CreateCommentRequest request = new CreateCommentRequest("nice", null, List.of(OWNER), List.of(OWNER_CAR));

        assertThatThrownBy(() -> service.addComment(AUTHOR.toString(), POST, request))
                .isInstanceOf(InvalidReferenceException.class);
    }

    @Test
    void addCommentDeduplicatesTagIdsServerSide() {
        profilesExist(OWNER);
        when(garageService.findCarOwnerIds(List.of(OWNER_CAR))).thenReturn(Map.of(OWNER_CAR, OWNER));

        service.addComment(AUTHOR.toString(), POST, new CreateCommentRequest(
                "nice", null, List.of(OWNER, OWNER), List.of(OWNER_CAR, OWNER_CAR, OWNER_CAR)));

        verify(commentTaggedPersonRepository, times(1)).save(any(CommentTaggedPersonEntity.class));
        verify(commentTaggedCarRepository, times(1)).save(any(CommentTaggedCarEntity.class));
    }

    // ---- the tags echoed back ------------------------------------------------

    @Test
    void addCommentEchoesTheTagsInTheCreateResponse() {
        profilesExist(AUTHOR, OWNER);
        when(garageService.findCarOwnerIds(List.of(OWNER_CAR))).thenReturn(Map.of(OWNER_CAR, OWNER));
        when(garageService.findCarsByIds(List.of(OWNER_CAR))).thenReturn(List.of(car(OWNER_CAR, OWNER)));

        CommentDto dto = service.addComment(AUTHOR.toString(), POST,
                new CreateCommentRequest("nice", null, List.of(OWNER), List.of(OWNER_CAR)));

        assertThat(dto.author()).isNotNull();
        assertThat(dto.author().id()).isEqualTo(AUTHOR);
        assertThat(dto.taggedPeople()).extracting(ProfileSearchResultDto::id).containsExactly(OWNER);
        assertThat(dto.taggedCars()).extracting(CarSummaryDto::id).containsExactly(OWNER_CAR);
        assertThat(dto.taggedCars().getFirst().owner().id()).isEqualTo(OWNER);
    }

    @Test
    void addCommentWithoutTagsReturnsEmptyLists() {
        profilesExist(AUTHOR);

        CommentDto dto = service.addComment(AUTHOR.toString(), POST,
                new CreateCommentRequest("nice", null, null, null));

        assertThat(dto.taggedPeople()).isEmpty();
        assertThat(dto.taggedCars()).isEmpty();
    }

    @Test
    void commentPageResolvesTagsAndSkipsThemForADeletedComment() {
        profilesExist(AUTHOR, OWNER);
        when(commentRepository.findRootCommentPage(any(), anyBoolean(), any(), any(), any()))
                .thenReturn(List.of(comment(COMMENT, AUTHOR, false), comment(DELETED_COMMENT, AUTHOR, true)));
        when(commentTaggedPersonRepository.findAllByIdCommentIdIn(List.of(COMMENT)))
                .thenReturn(List.of(taggedPerson(COMMENT, OWNER)));
        when(commentTaggedCarRepository.findAllByIdCommentIdIn(List.of(COMMENT)))
                .thenReturn(List.of(taggedCar(COMMENT, OWNER_CAR)));
        when(garageService.findCarsByIds(anyCollection())).thenReturn(List.of(car(OWNER_CAR, OWNER)));

        CommentPageDto page = service.getComments(AUTHOR, POST, null, 20);

        assertThat(page.items()).hasSize(2);
        CommentDto visible = page.items().getFirst();
        assertThat(visible.taggedPeople()).extracting(ProfileSearchResultDto::id).containsExactly(OWNER);
        assertThat(visible.taggedCars()).extracting(CarSummaryDto::id).containsExactly(OWNER_CAR);

        CommentDto deleted = page.items().get(1);
        assertThat(deleted.content()).isNull();
        assertThat(deleted.taggedPeople()).isEmpty();
        assertThat(deleted.taggedCars()).isEmpty();
    }

    // ---- notifications -------------------------------------------------------

    @Test
    void addCommentNotifiesEveryTaggedPersonAndFlagsTheCarOwner() {
        // The commenter is the post's author, so no post_comment event competes with the tag events.
        profilesExist(AUTHOR, OWNER, OTHER);
        when(garageService.findCarOwnerIds(List.of(OWNER_CAR))).thenReturn(Map.of(OWNER_CAR, OWNER));

        service.addComment(AUTHOR.toString(), POST,
                new CreateCommentRequest("nice", null, List.of(OWNER, OTHER), List.of(OWNER_CAR)));

        ArgumentCaptor<PostCommentTaggedEvent> captor = ArgumentCaptor.forClass(PostCommentTaggedEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(PostCommentTaggedEvent::recipientId, PostCommentTaggedEvent::carTagged)
                .containsExactlyInAnyOrder(Tuple.tuple(OWNER, true), Tuple.tuple(OTHER, false));
        assertThat(captor.getAllValues().getFirst().postId()).isEqualTo(POST);
        assertThat(captor.getAllValues().getFirst().actorId()).isEqualTo(AUTHOR);
        assertThat(captor.getAllValues().getFirst().commentId()).isNotNull();
    }

    @Test
    void commentTagsDoNotSelfNotify() {
        profilesExist(AUTHOR);

        service.addComment(AUTHOR.toString(), POST,
                new CreateCommentRequest("nice", null, List.of(AUTHOR), null));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createPostNotifiesEveryTaggedPersonAndFlagsTheCarOwner() {
        profilesExist(OWNER, OTHER);
        when(garageService.findCarOwnerIds(List.of(OWNER_CAR))).thenReturn(Map.of(OWNER_CAR, OWNER));

        service.createPost(AUTHOR.toString(), new CreatePostRequest(
                "caption", List.of(OWNER, OTHER), List.of(OWNER_CAR), null, null, null, null));

        ArgumentCaptor<PostTaggedEvent> captor = ArgumentCaptor.forClass(PostTaggedEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(PostTaggedEvent::recipientId, PostTaggedEvent::carTagged)
                .containsExactlyInAnyOrder(Tuple.tuple(OWNER, true), Tuple.tuple(OTHER, false));
        // createPost mints the id itself, so only its presence and the actor can be asserted here.
        assertThat(captor.getAllValues().getFirst().postId()).isNotNull();
        assertThat(captor.getAllValues().getFirst().actorId()).isEqualTo(AUTHOR);
    }

    @Test
    void updatePostOnlyNotifiesTheNewcomers() {
        profilesExist(OWNER, OTHER);
        when(taggedPersonRepository.findAllByIdPostId(POST)).thenReturn(List.of(postTaggedPerson(OTHER)));

        service.updatePost(AUTHOR.toString(), POST, new UpdatePostRequest(
                "edited", List.of(OTHER, OWNER), null, null, null, null, null));

        ArgumentCaptor<PostTaggedEvent> captor = ArgumentCaptor.forClass(PostTaggedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        assertThat(captor.getValue().recipientId()).isEqualTo(OWNER); // OTHER was already tagged
    }

    @Test
    void updatePostWithUnchangedTagListsNotifiesNobody() {
        when(taggedPersonRepository.findAllByIdPostId(POST)).thenReturn(List.of(postTaggedPerson(OTHER)));

        service.updatePost(AUTHOR.toString(), POST, new UpdatePostRequest(
                "edited", null, null, null, null, null, null));

        verify(taggedPersonRepository, never()).deleteAllByIdPostId(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ---- untagging yourself -------------------------------------------------

    @Test
    void untaggingYourselfFromAPostTakesYourOwnTaggedCarsWithIt() {
        when(postRepository.existsById(POST)).thenReturn(true);
        when(garageService.findCarIdsByOwner(OWNER)).thenReturn(List.of(OWNER_CAR));

        service.removeSelfTagsFromPost(OWNER, POST);

        // Leaving the car behind would break the "a tagged car's owner is tagged too" rule.
        verify(taggedPersonRepository).deleteByIdPostIdAndIdUserId(POST, OWNER);
        verify(taggedCarRepository).deleteByIdPostIdAndIdCarIdIn(POST, List.of(OWNER_CAR));
    }

    @Test
    void untaggingYourselfFromAPostLeavesOtherPeoplesTagsAlone() {
        when(postRepository.existsById(POST)).thenReturn(true);
        when(garageService.findCarIdsByOwner(OWNER)).thenReturn(List.of());

        service.removeSelfTagsFromPost(OWNER, POST);

        verify(taggedPersonRepository, never()).deleteAllByIdPostId(any());
        verify(taggedCarRepository, never()).deleteAllByIdPostId(any());
        verify(taggedCarRepository, never()).deleteByIdPostIdAndIdCarIdIn(any(), anyCollection());
    }

    @Test
    void untaggingFromAPostThatIsGoneIsNotFound() {
        when(postRepository.existsById(POST)).thenReturn(false);

        assertThatThrownBy(() -> service.removeSelfTagsFromPost(OWNER, POST))
                .isInstanceOf(PostNotFoundException.class);
        verify(taggedPersonRepository, never()).deleteByIdPostIdAndIdUserId(any(), any());
    }

    @Test
    void untaggingYourselfFromACommentTakesYourOwnTaggedCarsWithIt() {
        when(commentRepository.existsById(COMMENT)).thenReturn(true);
        when(garageService.findCarIdsByOwner(OWNER)).thenReturn(List.of(OWNER_CAR));

        service.removeSelfTagsFromComment(OWNER, COMMENT);

        verify(commentTaggedPersonRepository).deleteByIdCommentIdAndIdUserId(COMMENT, OWNER);
        verify(commentTaggedCarRepository).deleteByIdCommentIdAndIdCarIdIn(COMMENT, List.of(OWNER_CAR));
    }

    @Test
    void untaggingFromACommentThatIsGoneIsNotFound() {
        when(commentRepository.existsById(COMMENT)).thenReturn(false);

        assertThatThrownBy(() -> service.removeSelfTagsFromComment(OWNER, COMMENT))
                .isInstanceOf(CommentNotFoundException.class);
        verify(commentTaggedPersonRepository, never()).deleteByIdCommentIdAndIdUserId(any(), any());
    }

    private static CommentTaggedPersonEntity taggedPerson(UUID commentId, UUID userId) {
        CommentTaggedPersonEntity e = new CommentTaggedPersonEntity();
        e.setId(new CommentTaggedPersonId(commentId, userId));
        return e;
    }

    private static CommentTaggedCarEntity taggedCar(UUID commentId, UUID carId) {
        CommentTaggedCarEntity e = new CommentTaggedCarEntity();
        e.setId(new CommentTaggedCarId(commentId, carId));
        return e;
    }

    private static TaggedPersonEntity postTaggedPerson(UUID userId) {
        TaggedPersonEntity e = new TaggedPersonEntity();
        e.setId(new TaggedPersonId(POST, userId));
        return e;
    }
}
