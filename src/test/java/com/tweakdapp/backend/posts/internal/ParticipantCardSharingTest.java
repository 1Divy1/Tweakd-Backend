package com.tweakdapp.backend.posts.internal;

import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.mapevents.MapEventContestsService;
import com.tweakdapp.backend.mapevents.dto.ParticipantCardDto;
import com.tweakdapp.backend.posts.dto.request.ShareParticipantCardRequest;
import com.tweakdapp.backend.posts.exception.ParticipantCardCooldownException;
import com.tweakdapp.backend.posts.exception.ParticipantCardNotFoundException;
import com.tweakdapp.backend.posts.internal.entities.PostEntity;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
 * Sharing a participant card to the feed: it must be the caller's card, it creates an ordinary post
 * that tags the car and carries the card reference, and the same card cannot go out again inside the
 * repost cooldown — which the refusal says the end of.
 */
class ParticipantCardSharingTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID EVENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CAR = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    private PostRepository postRepository;
    private MapEventContestsService cards;
    private PostsServiceImpl service;
    private final PostEntity[] saved = new PostEntity[1];

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        cards = mock(MapEventContestsService.class);
        GarageService garageService = mock(GarageService.class);

        service = new PostsServiceImpl(
                postRepository,
                mock(PostImageRepository.class),
                mock(TaggedPersonRepository.class),
                mock(TaggedCarRepository.class),
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
                cards);
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));

        when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> {
            saved[0] = inv.getArgument(0);
            return saved[0];
        });
        when(postRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(saved[0]));
        // The author owns the tagged car, which is what makes tagging it legal.
        when(garageService.findCarOwnerIds(any())).thenReturn(Map.of(CAR, USER));
        when(cards.findOwnedParticipantCard(USER, EVENT, CAR)).thenReturn(Optional.of(
                new ParticipantCardDto(EVENT, "Casino Square Cars & Coffee", 247, null, null, List.of())));
    }

    @Test
    void sharingACardThatIsNotYoursIsANotFound() {
        when(cards.findOwnedParticipantCard(USER, EVENT, CAR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.shareParticipantCard(USER.toString(), request()))
                .isInstanceOf(ParticipantCardNotFoundException.class);
        verify(postRepository, never()).save(any());
    }

    @Test
    void aFirstShareCreatesAPostThatCarriesTheCard() {
        when(postRepository.findLastParticipantCardPostAt(EVENT, CAR)).thenReturn(null);

        service.shareParticipantCard(USER.toString(), request());

        verify(postRepository).lockParticipantCard(EVENT + ":" + CAR);
        assertThat(saved[0].getParticipantCardEventId()).isEqualTo(EVENT);
        assertThat(saved[0].getParticipantCardCarId()).isEqualTo(CAR);
        assertThat(saved[0].getDescription()).isEqualTo("What a night");
    }

    @Test
    void sharingAgainInsideTheCooldownIsRefusedWithWhenItReopens() {
        Instant last = Instant.now().minus(Duration.ofHours(1));
        when(postRepository.findLastParticipantCardPostAt(EVENT, CAR)).thenReturn(last);

        assertThatThrownBy(() -> service.shareParticipantCard(USER.toString(), request()))
                .isInstanceOfSatisfying(ParticipantCardCooldownException.class, e -> {
                    assertThat(e.getNextPostAllowedAt())
                            .isEqualTo(last.plus(PostsServiceImpl.PARTICIPANT_CARD_REPOST_COOLDOWN));
                    assertThat(e.getErrorCode()).isEqualTo("participant_card_cooldown");
                    assertThat(e.getDetails()).containsKey("next_post_allowed_at");
                });
        verify(postRepository, never()).save(any());
    }

    @Test
    void onceTheCooldownHasPassedTheCardMayBeSharedAgain() {
        when(postRepository.findLastParticipantCardPostAt(EVENT, CAR))
                .thenReturn(Instant.now().minus(PostsServiceImpl.PARTICIPANT_CARD_REPOST_COOLDOWN).minusSeconds(60));

        service.shareParticipantCard(USER.toString(), request());

        assertThat(saved[0]).isNotNull();
        assertThat(saved[0].getParticipantCardCarId()).isEqualTo(CAR);
    }

    private static ShareParticipantCardRequest request() {
        return new ShareParticipantCardRequest(EVENT, CAR, "What a night");
    }
}
