package com.tweakdapp.backend.admin.internal.repositories;

import com.tweakdapp.backend.admin.internal.entities.AdminTeamMemberEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminTeamMemberRepository extends JpaRepository<AdminTeamMemberEntity, UUID> {

    List<AdminTeamMemberEntity> findAllByOrderByCreatedAtAsc();

    boolean existsByRole(String role);

    boolean existsByEmail(String email);

    /**
     * Loads a member {@code FOR UPDATE}. Ownership transfer takes both rows this way, so two
     * transfers racing serialise instead of both demoting the owner and leaving the team with none.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from AdminTeamMemberEntity m where m.userId = :userId")
    Optional<AdminTeamMemberEntity> lockById(@Param("userId") UUID userId);
}
