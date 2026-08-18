package com.carsocialmedia.backend.feedbackfeed.internal;

import com.carsocialmedia.backend.feedbackfeed.dto.FeedbackFeedPageDto;
import com.carsocialmedia.backend.feedbackfeed.dto.FeedbackMessageDto;
import com.carsocialmedia.backend.feedbackfeed.dto.request.SubmitFeedbackMessageRequest;
import com.carsocialmedia.backend.feedbackfeed.exception.FeedbackDeletionClosedException;
import com.carsocialmedia.backend.feedbackfeed.exception.FeedbackMessageNotFoundException;
import com.carsocialmedia.backend.feedbackfeed.exception.FeedbackVotingClosedException;
import com.carsocialmedia.backend.feedbackfeed.exception.InvalidFeedbackFeedCursorException;
import com.carsocialmedia.backend.feedbackfeed.exception.InvalidFeedbackFeedRequestException;
import com.carsocialmedia.backend.feedbackfeed.exception.NotFeedbackAuthorException;
import com.carsocialmedia.backend.feedbackfeed.internal.entities.FeedbackFeedMessageEntity;
import com.carsocialmedia.backend.feedbackfeed.internal.entities.FeedbackFeedStatusOptionEntity;
import com.carsocialmedia.backend.feedbackfeed.internal.entities.FeedbackFeedTypeOptionEntity;
import com.carsocialmedia.backend.feedbackfeed.internal.entities.FeedbackFeedVoteEntity;
import com.carsocialmedia.backend.feedbackfeed.internal.entities.FeedbackFeedVoteId;
import com.carsocialmedia.backend.feedbackfeed.internal.repositories.FeedbackFeedMessageRepository;
import com.carsocialmedia.backend.feedbackfeed.internal.repositories.FeedbackFeedStatusOptionRepository;
import com.carsocialmedia.backend.feedbackfeed.internal.repositories.FeedbackFeedTypeOptionRepository;
import com.carsocialmedia.backend.feedbackfeed.internal.repositories.FeedbackFeedVoteRepository;
import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure-unit behaviour of {@link FeedbackFeedServiceImpl} with every collaborator mocked: the
 * three-way vote toggle, the asymmetry between an author's delete and a staff removal, the rules
 * around a shipped message, and the cursor/sort validation that keeps a client from replaying a
 * "popular" token against a time-ordered feed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedbackFeedServiceImplTest {

    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID VOTER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    @Mock
    private FeedbackFeedMessageRepository messageRepository;
    @Mock
    private FeedbackFeedVoteRepository voteRepository;
    @Mock
    private FeedbackFeedTypeOptionRepository typeRepository;
    @Mock
    private FeedbackFeedStatusOptionRepository statusRepository;
    @Mock
    private ProfileService profileService;
    @Mock
    private EntityManager entityManager;

    private FeedbackFeedServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FeedbackFeedServiceImpl(messageRepository, voteRepository, typeRepository,
                statusRepository, profileService, entityManager);

        when(typeRepository.findAll()).thenReturn(List.of(
                typeOption("bug", "Bug"),
                typeOption("feature_request", "Feature request"),
                typeOption("feature_improvement", "Feature improvement")));
        when(statusRepository.findAll()).thenReturn(List.of(
                statusOption("completed", "Completed"),
                statusOption("sent", "Sent"),
                statusOption("under_development", "Under development")));
        when(typeRepository.existsById(eq("bug"))).thenReturn(true);
        when(statusRepository.existsById(any())).thenReturn(true);
        when(profileService.findByIds(any())).thenReturn(List.of(
                new ProfileSearchResultDto(AUTHOR, "Dana Wheelwell", "wheelwell_dana", "https://cdn/a.png")));
        when(voteRepository.findByIdUserIdAndIdMessageIdIn(any(), any())).thenReturn(List.of());
    }

    // ---- reference data -----------------------------------------------------

    @Test
    void statusesComeBackInRoadmapOrderNotTheOrderTheDatabaseReturnedThem() {
        assertThat(service.listStatuses())
                .extracting(dto -> dto.id())
                .containsExactly("sent", "under_development", "completed");
    }

    // ---- the card ------------------------------------------------------------

    @Test
    void aCardCarriesTheOriginalMessageAndTheStaffResponseSideBySide() {
        FeedbackFeedMessageEntity message = message("sent");
        message.setStaffResponseMessage("Fixed in 2.4.");
        stubVisible(message);

        FeedbackMessageDto dto = service.getMessage(VOTER, MESSAGE_ID);

        assertThat(dto.message()).isEqualTo("Cover photo resets when I reorder the gallery.");
        assertThat(dto.staffResponse()).isEqualTo("Fixed in 2.4.");
        assertThat(dto.type().label()).isEqualTo("Bug");
        assertThat(dto.status().label()).isEqualTo("Sent");
    }

    @Test
    void theAuthorSeesTheirOwnCardMarkedAsTheirsAndOtherViewersDoNot() {
        stubVisible(message("sent"));

        assertThat(service.getMessage(AUTHOR, MESSAGE_ID).viewerIsAuthor()).isTrue();
        assertThat(service.getMessage(VOTER, MESSAGE_ID).viewerIsAuthor()).isFalse();
    }

    @Test
    void theViewersOwnVoteIsResolvedOntoTheCard() {
        stubVisible(message("sent"));
        when(voteRepository.findByIdUserIdAndIdMessageIdIn(eq(VOTER), any()))
                .thenReturn(List.of(vote(VOTER, FeedbackFeedVoteEntity.DOWN)));

        assertThat(service.getMessage(VOTER, MESSAGE_ID).myVote()).isEqualTo(-1);
    }

    @Test
    void aStaffRemovedMessageReadsAsMissing() {
        when(messageRepository.findByIdAndDeletedFalse(MESSAGE_ID)).thenReturn(Optional.empty());

        assertThatExceptionOfType(FeedbackMessageNotFoundException.class)
                .isThrownBy(() -> service.getMessage(VOTER, MESSAGE_ID));
    }

    // ---- voting --------------------------------------------------------------

    @Test
    void aFirstVoteIsPersisted() {
        stubVisible(message("sent"));
        when(voteRepository.findById(any())).thenReturn(Optional.empty());

        service.vote(VOTER, MESSAGE_ID, 1);

        verify(entityManager).persist(any(FeedbackFeedVoteEntity.class));
        verify(voteRepository, never()).delete(any());
    }

    @Test
    void votingTheSameDirectionAgainWithdrawsTheVote() {
        stubVisible(message("sent"));
        FeedbackFeedVoteEntity existing = vote(VOTER, FeedbackFeedVoteEntity.UP);
        when(voteRepository.findById(any())).thenReturn(Optional.of(existing));

        service.vote(VOTER, MESSAGE_ID, 1);

        verify(voteRepository).delete(existing);
        verify(entityManager, never()).persist(any());
    }

    @Test
    void votingTheOppositeDirectionSwitchesTheExistingVoteInPlace() {
        stubVisible(message("sent"));
        FeedbackFeedVoteEntity existing = vote(VOTER, FeedbackFeedVoteEntity.UP);
        when(voteRepository.findById(any())).thenReturn(Optional.of(existing));

        service.vote(VOTER, MESSAGE_ID, -1);

        assertThat(existing.getVoteType()).isEqualTo(FeedbackFeedVoteEntity.DOWN);
        assertThat(existing.getUpdatedAt()).isNotNull();
        verify(voteRepository, never()).delete(any());
        verify(entityManager, never()).persist(any());
    }

    @Test
    void anAuthorMayVoteOnTheirOwnMessage() {
        stubVisible(message("sent"));
        when(voteRepository.findById(any())).thenReturn(Optional.empty());

        service.vote(AUTHOR, MESSAGE_ID, 1);

        verify(entityManager).persist(any(FeedbackFeedVoteEntity.class));
    }

    @Test
    void aVoteValueOtherThanPlusOrMinusOneIsRejected() {
        stubVisible(message("sent"));

        assertThatExceptionOfType(InvalidFeedbackFeedRequestException.class)
                .isThrownBy(() -> service.vote(VOTER, MESSAGE_ID, 5));
    }

    @Test
    void votingOnACompletedMessageIsRejectedBeforeItReachesTheDatabase() {
        stubVisible(message("completed"));

        assertThatExceptionOfType(FeedbackVotingClosedException.class)
                .isThrownBy(() -> service.vote(VOTER, MESSAGE_ID, 1));
        assertThatExceptionOfType(FeedbackVotingClosedException.class)
                .isThrownBy(() -> service.removeVote(VOTER, MESSAGE_ID));
        verify(entityManager, never()).persist(any());
        verify(voteRepository, never()).deleteById(any());
    }

    // ---- deletion ------------------------------------------------------------

    @Test
    void anAuthorsOwnDeleteRemovesTheRowOutright() {
        FeedbackFeedMessageEntity message = message("sent");
        stubVisible(message);

        service.deleteOwn(AUTHOR, MESSAGE_ID);

        verify(messageRepository).delete(message);
        assertThat(message.isDeleted()).isFalse();
    }

    @Test
    void oneUserCannotDeleteAnothersMessage() {
        stubVisible(message("sent"));

        assertThatExceptionOfType(NotFeedbackAuthorException.class)
                .isThrownBy(() -> service.deleteOwn(VOTER, MESSAGE_ID));
        verify(messageRepository, never()).delete(any(FeedbackFeedMessageEntity.class));
    }

    @Test
    void anAuthorCannotDeleteOnceStaffHaveStartedWorkOnIt() {
        stubVisible(message("under_development"));

        assertThatExceptionOfType(FeedbackDeletionClosedException.class)
                .isThrownBy(() -> service.deleteOwn(AUTHOR, MESSAGE_ID));
        verify(messageRepository, never()).delete(any(FeedbackFeedMessageEntity.class));
    }

    @Test
    void anAuthorCannotDeleteOnceItHasShipped() {
        stubVisible(message("completed"));

        assertThatExceptionOfType(FeedbackDeletionClosedException.class)
                .isThrownBy(() -> service.deleteOwn(AUTHOR, MESSAGE_ID));
        verify(messageRepository, never()).delete(any(FeedbackFeedMessageEntity.class));
    }

    @Test
    void staffCanStillRemoveAMessageTheAuthorCanNoLongerDelete() {
        FeedbackFeedMessageEntity message = message("completed");
        when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));

        service.removeAsStaff(MESSAGE_ID);

        assertThat(message.isDeleted()).isTrue();
    }

    @Test
    void aStaffRemovalFlagsTheRowRatherThanDeletingIt() {
        FeedbackFeedMessageEntity message = message("sent");
        when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));

        service.removeAsStaff(MESSAGE_ID);

        assertThat(message.isDeleted()).isTrue();
        verify(messageRepository, never()).delete(any(FeedbackFeedMessageEntity.class));
    }

    @Test
    void removingAnAlreadyRemovedMessageStillSucceeds() {
        FeedbackFeedMessageEntity message = message("sent");
        message.setDeleted(true);
        // findById, not findByIdAndDeletedFalse — otherwise the second call would 404.
        when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));

        service.removeAsStaff(MESSAGE_ID);

        assertThat(message.isDeleted()).isTrue();
    }

    // ---- publishing ----------------------------------------------------------

    @Test
    void submittingWithAnUnknownCategoryIsRejected() {
        assertThatExceptionOfType(InvalidFeedbackFeedRequestException.class)
                .isThrownBy(() -> service.submit(AUTHOR,
                        new SubmitFeedbackMessageRequest("wishlist", "Please add a dark mode.")));
        verify(messageRepository, never()).save(any());
    }

    @Test
    void submittingTrimsTheMessageAndLeavesStatusToTheDatabase() {
        stubVisible(message("sent"));

        service.submit(AUTHOR, new SubmitFeedbackMessageRequest("bug", "  Cover photo resets.  "));

        verify(messageRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getMessage().equals("Cover photo resets.")
                        && saved.getAuthorId().equals(AUTHOR)
                        && saved.getType().equals("bug")
                        && saved.getStatus() == null
                        && saved.getId() != null));
    }

    // ---- feed sorts and cursors ---------------------------------------------

    @Test
    void anUnknownSortIsRejected() {
        assertThatExceptionOfType(InvalidFeedbackFeedRequestException.class)
                .isThrownBy(() -> service.getFeed(VOTER, "trending", null, 20));
    }

    @Test
    void theDefaultSortIsNewest() {
        when(messageRepository.findNewest(anyBoolean(), any(), any(), anyInt())).thenReturn(List.of());

        service.getFeed(VOTER, null, null, 20);

        verify(messageRepository).findNewest(eq(true), eq(null), eq(null), eq(21));
    }

    @Test
    void aPopularCursorCannotBeReplayedAgainstATimeOrderedFeed() {
        when(messageRepository.findMostVoted(anyBoolean(), any(), any(), anyInt()))
                .thenReturn(List.of(message("sent"), message("sent")));

        String popularCursor = service.getFeed(VOTER, "popular", null, 1).nextCursor();
        assertThat(popularCursor).isNotNull();

        assertThatExceptionOfType(InvalidFeedbackFeedCursorException.class)
                .isThrownBy(() -> service.getFeed(VOTER, "newest", popularCursor, 20));
    }

    @Test
    void aGarbageCursorIsRejected() {
        assertThatExceptionOfType(InvalidFeedbackFeedCursorException.class)
                .isThrownBy(() -> service.getFeed(VOTER, "newest", "not-a-cursor", 20));
    }

    @Test
    void aFullPageEmitsACursorAndAShortPageDoesNot() {
        when(messageRepository.findNewest(anyBoolean(), any(), any(), anyInt()))
                .thenReturn(List.of(message("sent"), message("sent")));
        assertThat(service.getFeed(VOTER, "newest", null, 1).nextCursor()).isNotNull();

        when(messageRepository.findNewest(anyBoolean(), any(), any(), anyInt()))
                .thenReturn(List.of(message("sent")));
        FeedbackFeedPageDto lastPage = service.getFeed(VOTER, "newest", null, 20);
        assertThat(lastPage.nextCursor()).isNull();
        assertThat(lastPage.items()).hasSize(1);
    }

    @Test
    void theCompletedSectionPagesOnShipDate() {
        FeedbackFeedMessageEntity shipped = message("completed");
        shipped.setCompletedAt(Instant.parse("2026-08-01T10:15:30Z"));
        FeedbackFeedMessageEntity overflow = message("completed");
        when(messageRepository.findCompleted(anyBoolean(), any(), any(), anyInt()))
                .thenReturn(List.of(shipped, overflow));

        String cursor = service.getCompleted(VOTER, null, 1).nextCursor();

        // Decodes as a time cursor, so it is accepted by the completed section on the way back in.
        service.getCompleted(VOTER, cursor, 1);
        verify(messageRepository).findCompleted(eq(false), eq(Instant.parse("2026-08-01T10:15:30Z")),
                eq(shipped.getId()), eq(2));
    }

    @Test
    void theDashboardListResolvesNoViewerVoteBecauseStaffHaveNoProfile() {
        when(messageRepository.findForAdmin(any(), any(), anyBoolean(), anyBoolean(), any(), any(), anyInt()))
                .thenReturn(List.of(message("sent")));

        FeedbackFeedPageDto page = service.listAll(null, null, false, null, 20);

        assertThat(page.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.myVote()).isNull();
                    assertThat(item.viewerIsAuthor()).isFalse();
                });
        verify(voteRepository, never()).findByIdUserIdAndIdMessageIdIn(any(), any());
    }

    @Test
    void blankAdminFiltersReachTheQueryAsNulls() {
        when(messageRepository.findForAdmin(any(), any(), anyBoolean(), anyBoolean(), any(), any(), anyInt()))
                .thenReturn(List.of());

        service.listAll("  ", "", false, null, 20);

        verify(messageRepository).findForAdmin(eq(null), eq(null), eq(false), eq(true), eq(null), eq(null), eq(21));
    }

    @Test
    void pageSizeIsClamped() {
        when(messageRepository.findNewest(anyBoolean(), any(), any(), anyInt())).thenReturn(List.of());

        service.getFeed(VOTER, "newest", null, 5000);
        verify(messageRepository).findNewest(eq(true), eq(null), eq(null), eq(51));

        service.getFeed(VOTER, "newest", null, 0);
        verify(messageRepository).findNewest(eq(true), eq(null), eq(null), eq(2));
    }

    // ---- admin writes --------------------------------------------------------

    @Test
    void anUnknownStatusIsRejected() {
        when(statusRepository.existsById("shipped")).thenReturn(false);

        assertThatExceptionOfType(InvalidFeedbackFeedRequestException.class)
                .isThrownBy(() -> service.updateStatus(MESSAGE_ID, "shipped"));
    }

    @Test
    void aBlankResponseClearsTheExistingOne() {
        FeedbackFeedMessageEntity message = message("sent");
        message.setStaffResponseMessage("Looking into it.");
        stubVisible(message);

        service.respond(MESSAGE_ID, "   ");

        assertThat(message.getStaffResponseMessage()).isNull();
    }

    // ---- helpers -------------------------------------------------------------

    /**
     * Both lookups the service uses after a write, so {@code reloadAndAssemble} finds the row.
     * {@code entityManager.refresh} is a no-op against a mock, which is fine here — the trigger
     * behaviour it exists for is covered by the repository IT.
     */
    private void stubVisible(FeedbackFeedMessageEntity message) {
        when(messageRepository.findByIdAndDeletedFalse(any())).thenReturn(Optional.of(message));
        // any(), not the fixed id: submit() reloads by the id it generated, which the test cannot know.
        when(messageRepository.findById(any())).thenReturn(Optional.of(message));
    }

    private FeedbackFeedMessageEntity message(String status) {
        FeedbackFeedMessageEntity message = new FeedbackFeedMessageEntity();
        message.setId(MESSAGE_ID);
        message.setAuthorId(AUTHOR);
        message.setMessage("Cover photo resets when I reorder the gallery.");
        message.setType("bug");
        message.setStatus(status);
        message.setCreatedAt(Instant.parse("2026-08-15T09:41:00Z"));
        return message;
    }

    private FeedbackFeedVoteEntity vote(UUID userId, short direction) {
        FeedbackFeedVoteEntity vote = new FeedbackFeedVoteEntity();
        vote.setId(new FeedbackFeedVoteId(userId, MESSAGE_ID));
        vote.setVoteType(direction);
        return vote;
    }

    private static FeedbackFeedTypeOptionEntity typeOption(String id, String label) {
        FeedbackFeedTypeOptionEntity option = new FeedbackFeedTypeOptionEntity();
        option.setId(id);
        option.setType(label);
        return option;
    }

    private static FeedbackFeedStatusOptionEntity statusOption(String id, String label) {
        FeedbackFeedStatusOptionEntity option = new FeedbackFeedStatusOptionEntity();
        option.setId(id);
        option.setStatus(label);
        return option;
    }
}
