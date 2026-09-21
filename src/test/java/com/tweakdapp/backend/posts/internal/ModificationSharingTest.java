package com.tweakdapp.backend.posts.internal;

import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.mapevents.MapEventContestsService;
import com.tweakdapp.backend.posts.dto.request.ShareModificationRequest;
import com.tweakdapp.backend.posts.exception.ModificationNotFoundException;
import com.tweakdapp.backend.posts.internal.entities.PostEntity;
import com.tweakdapp.backend.posts.internal.entities.TaggedCarEntity;
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
import com.tweakdapp.backend.report.ReportService;
import com.tweakdapp.backend.storage.StorageService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sharing a build-log modification to the feed: it must sit on a car the caller owns, it creates an
 * ordinary post that tags that car and carries the modification's id, and a mod that has already
 * been shared hands back its existing post rather than posting twice.
 */
class ModificationSharingTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID CAR = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID MOD = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    private PostRepository postRepository;
    private GarageService garageService;
    private TaggedCarRepository taggedCarRepository;
    private PostsServiceImpl service;
    private final PostEntity[] saved = new PostEntity[1];

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        garageService = mock(GarageService.class);
        taggedCarRepository = mock(TaggedCarRepository.class);

        service = new PostsServiceImpl(
                postRepository,
                mock(PostImageRepository.class),
                mock(TaggedPersonRepository.class),
                taggedCarRepository,
                mock(CommentRepository.class),
                mock(CommentLikeRepository.class),
                mock(CommentTaggedPersonRepository.class),
                mock(CommentTaggedCarRepository.class),
                mock(PostLikeRepository.class),
                mock(SavedPostRepository.class),
                mock(PostShareRepository.class),
                mock(ProfileService.class),
                garageService,
                mock(StorageService.class),
                mock(ReportService.class),
                mock(ApplicationEventPublisher.class),
                mock(MapEventContestsService.class));
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));

        when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> {
            saved[0] = inv.getArgument(0);
            return saved[0];
        });
        when(postRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(saved[0]));
        when(postRepository.findByModShareModificationId(MOD)).thenReturn(Optional.empty());
        // The mod is on the caller's own car, which is also what makes tagging that car legal.
        when(garageService.findOwnedModificationCarId(USER, MOD)).thenReturn(Optional.of(CAR));
        when(garageService.findCarOwnerIds(any())).thenReturn(Map.of(CAR, USER));
    }

    @Test
    void sharingAModThatIsNotOnYourCarIsANotFound() {
        when(garageService.findOwnedModificationCarId(USER, MOD)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.shareModification(USER.toString(), request()))
                .isInstanceOf(ModificationNotFoundException.class);
        verify(postRepository, never()).save(any());
    }

    @Test
    void aFirstShareCreatesAPostThatCarriesTheMod() {
        service.shareModification(USER.toString(), request());

        verify(postRepository).lockParticipantCard("mod:" + MOD);
        assertThat(saved[0].getModShareModificationId()).isEqualTo(MOD);
        // No caption: the mod's own description is what the card shows.
        assertThat(saved[0].getDescription()).isEmpty();
        // It is an ordinary post otherwise — nothing about it is a participant card.
        assertThat(saved[0].getParticipantCardEventId()).isNull();
    }

    @Test
    void theSharedModsCarIsTaggedOnThePost() {
        service.shareModification(USER.toString(), request());

        // The tag is what puts the mod among the car's tagged posts and gives the card its link.
        ArgumentCaptor<TaggedCarEntity> tag = ArgumentCaptor.forClass(TaggedCarEntity.class);
        verify(taggedCarRepository).save(tag.capture());
        assertThat(tag.getValue().getId().getCarId()).isEqualTo(CAR);
    }

    @Test
    void sharingAModThatIsAlreadySharedReturnsTheFirstPostInsteadOfASecond() {
        PostEntity existing = new PostEntity();
        existing.setId(UUID.fromString("00000000-0000-0000-0000-0000000000e1"));
        existing.setUserId(USER);
        existing.setDescription("");
        existing.setModShareModificationId(MOD);
        existing.setLikesCount(0L);
        existing.setCommentsCount(0L);
        existing.setSharesCount(0L);
        existing.setSavedCount(0L);
        when(postRepository.findByModShareModificationId(MOD)).thenReturn(Optional.of(existing));
        when(garageService.findModShareCards(any())).thenReturn(Map.of());

        service.shareModification(USER.toString(), request());

        verify(postRepository, never()).save(any(PostEntity.class));
    }

    private static ShareModificationRequest request() {
        return new ShareModificationRequest(MOD);
    }
}
