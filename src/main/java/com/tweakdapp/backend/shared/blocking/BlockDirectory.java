package com.tweakdapp.backend.shared.blocking;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only view of {@code blocked_accounts}, implemented by the {@code relationships} module (the
 * owner of blocking). Lives in {@code shared} so every content module can hide blocked accounts
 * without depending on {@code relationships}, which itself depends on {@code profile} — a cycle for
 * {@code profile} and anything below it.
 *
 * <p>A block is <strong>two-way</strong>: once either side blocks the other, neither sees the other's
 * profile, content, or search result. Every method here is symmetric for that reason.
 */
public interface BlockDirectory {

    /**
     * Placeholder id used when there is nobody to hide. JPQL {@code not in :ids} with an empty
     * collection is unsafe (Hibernate may render {@code not in (null)}, which filters every row), so
     * queries always receive at least this id, which matches no profile.
     */
    UUID NOBODY = new UUID(0L, 0L);

    /** Every account hidden from {@code viewerId}: the ones they blocked and the ones blocking them. */
    Set<UUID> hiddenFrom(UUID viewerId);

    /** Whether {@code viewerId} and {@code otherUserId} are separated by a block, in either direction. */
    boolean isHidden(UUID viewerId, UUID otherUserId);

    /** A hidden-id set shaped for a {@code not in :hiddenIds} query parameter — never empty. */
    static Collection<UUID> asQueryParam(Collection<UUID> hiddenIds) {
        return hiddenIds == null || hiddenIds.isEmpty() ? List.of(NOBODY) : hiddenIds;
    }
}
