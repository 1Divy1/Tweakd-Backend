package com.tweakdapp.backend.forums.internal.repositories;

import com.tweakdapp.backend.forums.internal.entities.ForumShortcutEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ForumShortcutRepository extends JpaRepository<ForumShortcutEntity, UUID> {

    /** The user's shortcuts in their pinned order. */
    List<ForumShortcutEntity> findByUserIdOrderBySortOrderAsc(UUID userId);

    /** A single shortcut scoped to its owner — the owner-scoping guard for mutations. */
    Optional<ForumShortcutEntity> findByIdAndUserId(UUID id, UUID userId);
}
