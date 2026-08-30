package com.tweakdapp.backend.dms.internal.events;

import java.util.UUID;

/**
 * Published inside the mark-read transaction; after commit the peer gets their "read" ticks and the
 * reader's other devices sync their badges.
 */
public record DmConversationReadEvent(UUID conversationId, UUID readerId, UUID peerId, UUID lastReadMessageId) {}
