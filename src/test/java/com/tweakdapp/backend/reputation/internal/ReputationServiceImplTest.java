package com.tweakdapp.backend.reputation.internal;

import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.profile.dto.ReputationAdjustmentDto;
import com.tweakdapp.backend.reputation.ReputationReasons;
import com.tweakdapp.backend.reputation.ReputationSourceType;
import com.tweakdapp.backend.reputation.dto.ReputationEntryDto;
import com.tweakdapp.backend.reputation.dto.ReputationSource;
import com.tweakdapp.backend.reputation.internal.entity.ReputationReasonEntity;
import com.tweakdapp.backend.reputation.internal.entity.ReputationScoreHistoryEntity;
import com.tweakdapp.backend.reputation.internal.repository.ReputationReasonRepository;
import com.tweakdapp.backend.reputation.internal.repository.ReputationScoreHistoryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The {@code reputation.enabled} pause switch. Reputation is paused until the app has users: while
 * the switch is off, no caller may move a score or write a history row, and the award/revoke
 * methods must say so through their return values rather than throw.
 */
@ExtendWith(MockitoExtension.class)
class ReputationServiceImplTest {

    private static final UUID USER = UUID.randomUUID();
    private static final ReputationSource SOURCE =
            new ReputationSource(ReputationSourceType.CONTEST, UUID.randomUUID(), "Best stance · Cluj meet");

    @Mock private ReputationScoreHistoryRepository historyRepository;
    @Mock private ReputationReasonRepository reasonRepository;
    @Mock private ProfileService profileService;
    @Mock private EntityManager entityManager;

    private ReputationServiceImpl service(boolean enabled) {
        ReputationServiceImpl service =
                new ReputationServiceImpl(historyRepository, reasonRepository, profileService, enabled);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        return service;
    }

    @Test
    void pausedAwardsTouchNothing() {
        ReputationServiceImpl service = service(false);

        assertThat(service.award(USER, ReputationReasons.CONTEST_FIRST_PLACE)).isNull();
        assertThat(service.award(USER, ReputationReasons.CONTEST_FIRST_PLACE, SOURCE)).isNull();
        assertThat(service.award(USER, ReputationReasons.CONTEST_FIRST_PLACE, 50)).isNull();
        assertThat(service.award(USER, ReputationReasons.CONTEST_FIRST_PLACE, 50, SOURCE)).isNull();

        verifyNoInteractions(profileService, historyRepository, reasonRepository);
    }

    @Test
    void pausedRevokeTouchesNothing() {
        assertThat(service(false).revoke(USER, ReputationReasons.CONTEST_FIRST_PLACE, SOURCE, "corrected"))
                .isFalse();

        verifyNoInteractions(profileService, historyRepository, reasonRepository);
    }

    /** The switch must actually switch: turned back on, an award moves the score again. */
    @Test
    void enabledAwardMovesTheScore() {
        ReputationReasonEntity reason = new ReputationReasonEntity();
        reason.setId(ReputationReasons.CONTEST_FIRST_PLACE);
        reason.setLabel("Won the first place");
        reason.setPoints(50);
        reason.setCategory("contests");
        reason.setRepeatable(true);
        reason.setActive(true);
        when(reasonRepository.findByIdAndActiveTrue(ReputationReasons.CONTEST_FIRST_PLACE))
                .thenReturn(Optional.of(reason));
        when(historyRepository.findLiveBySource(USER, reason.getId(), SOURCE.type(), SOURCE.id()))
                .thenReturn(Optional.empty());
        when(profileService.applyReputationDelta(USER, 50)).thenReturn(new ReputationAdjustmentDto(0, 50));
        when(historyRepository.saveAndFlush(any(ReputationScoreHistoryEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReputationEntryDto entry = service(true).award(USER, ReputationReasons.CONTEST_FIRST_PLACE, SOURCE);

        assertThat(entry).isNotNull();
        assertThat(entry.scoreGain()).isEqualTo(50);
        verify(profileService).applyReputationDelta(USER, 50);
    }
}
