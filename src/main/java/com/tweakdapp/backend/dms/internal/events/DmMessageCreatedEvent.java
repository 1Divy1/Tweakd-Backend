package com.tweakdapp.backend.dms.internal.events;

import com.tweakdapp.backend.dms.dto.DmMessageDto;

import java.util.UUID;

/** Published inside the send transaction; pushed to the sockets only after commit. */
public record DmMessageCreatedEvent(DmMessageDto message, UUID recipientId, UUID senderId) {}
