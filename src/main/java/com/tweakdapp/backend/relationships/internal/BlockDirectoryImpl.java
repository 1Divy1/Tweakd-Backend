package com.tweakdapp.backend.relationships.internal;

import com.tweakdapp.backend.shared.blocking.BlockDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Component
class BlockDirectoryImpl implements BlockDirectory {

    private final BlockedAccountRepository blockedAccountRepository;

    BlockDirectoryImpl(BlockedAccountRepository blockedAccountRepository) {
        this.blockedAccountRepository = blockedAccountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> hiddenFrom(UUID viewerId) {
        if (viewerId == null) {
            return Set.of();
        }
        return Set.copyOf(blockedAccountRepository.findCounterpartIds(viewerId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isHidden(UUID viewerId, UUID otherUserId) {
        if (viewerId == null || otherUserId == null || viewerId.equals(otherUserId)) {
            return false;
        }
        return blockedAccountRepository.existsBetween(viewerId, otherUserId);
    }
}
