package com.carsocialmedia.backend.admin.internal;

import com.carsocialmedia.backend.admin.exception.MissingCapabilityException;
import com.carsocialmedia.backend.admin.exception.NotTeamMemberException;
import com.carsocialmedia.backend.admin.internal.entities.AdminTeamMemberEntity;
import com.carsocialmedia.backend.admin.internal.repositories.AdminTeamMemberRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * The second auth layer behind the {@code ROLE_ADMIN} security gate: every admin endpoint calls
 * {@link #require} with the capability it needs, and this resolves the caller's team row, checks
 * the role's capability matrix ({@link AdminRole}), and touches {@code last_active_at} (throttled,
 * so it isn't a write per request).
 */
@Component
public class AdminAccessService {

    /** How stale last_active_at may get before we bother writing it again. */
    private static final Duration ACTIVITY_TOUCH_INTERVAL = Duration.ofMinutes(5);

    private final AdminTeamMemberRepository teamRepository;

    AdminAccessService(AdminTeamMemberRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    /**
     * The caller's team membership, or 403 if they have none / their role lacks the capability.
     * Runs in the caller's transaction; the returned entity is managed.
     */
    @Transactional
    public AdminTeamMemberEntity require(UUID userId, Capability capability) {
        AdminTeamMemberEntity member = teamRepository.findById(userId)
                .orElseThrow(NotTeamMemberException::new);
        if (!roleOf(member).can(capability)) {
            throw new MissingCapabilityException(capability.name());
        }
        touch(member);
        return member;
    }

    /** Membership check only, for endpoints every team member may use (e.g. viewing the team). */
    @Transactional
    public AdminTeamMemberEntity requireMember(UUID userId) {
        AdminTeamMemberEntity member = teamRepository.findById(userId)
                .orElseThrow(NotTeamMemberException::new);
        touch(member);
        return member;
    }

    static AdminRole roleOf(AdminTeamMemberEntity member) {
        return AdminRole.valueOf(member.getRole());
    }

    private void touch(AdminTeamMemberEntity member) {
        Instant now = Instant.now();
        if (member.getLastActiveAt() == null
                || member.getLastActiveAt().isBefore(now.minus(ACTIVITY_TOUCH_INTERVAL))) {
            member.setLastActiveAt(now);
            if (!"active".equals(member.getStatus())) {
                member.setStatus("active"); // first sign of life flips an invited member to active
            }
            teamRepository.save(member);
        }
    }
}
