package com.tweakdapp.backend.dms.internal.repositories;

import com.tweakdapp.backend.dms.internal.entities.DmMessageCarTagEntity;
import com.tweakdapp.backend.dms.internal.entities.DmMessageCarTagId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DmMessageCarTagRepository extends JpaRepository<DmMessageCarTagEntity, DmMessageCarTagId> {

    /** Batch variant for assembling a page of messages; group by {@code id.messageId} on the caller side. */
    List<DmMessageCarTagEntity> findAllByIdMessageIdIn(Collection<UUID> messageIds);
}
