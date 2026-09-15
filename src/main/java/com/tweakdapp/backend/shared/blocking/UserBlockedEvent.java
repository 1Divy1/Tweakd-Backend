package com.tweakdapp.backend.shared.blocking;

import java.util.UUID;

/**
 * Published by the {@code relationships} module after {@code blockerId} blocks {@code blockedId}
 * (only on a new block, never on a repeat). Modules that keep per-pair state the block should clear —
 * notifications between the two, DM unread badges — listen for it after commit.
 *
 * <p>Lives in {@code shared} next to {@link BlockDirectory} so listeners need no dependency on
 * {@code relationships}. The blocked user is never told.
 */
public record UserBlockedEvent(UUID blockerId, UUID blockedId) {}
