package com.carsocialmedia.backend.admin.internal.repositories;

import com.carsocialmedia.backend.admin.internal.entities.AdminTeamMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdminTeamMemberRepository extends JpaRepository<AdminTeamMemberEntity, UUID> {

    List<AdminTeamMemberEntity> findAllByOrderByCreatedAtAsc();

    boolean existsByRole(String role);

    boolean existsByEmail(String email);
}
