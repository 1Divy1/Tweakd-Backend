package com.tweakdapp.backend.dms.internal.events;

import java.util.UUID;

/** Published inside the delete transaction; pushed to both participants' sockets after commit. */
public record DmMessageDeletedEvent(UUID conversationId, UUID messageId, UUID userA, UUID userB) {}
