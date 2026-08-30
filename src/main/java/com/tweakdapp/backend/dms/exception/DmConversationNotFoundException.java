package com.tweakdapp.backend.dms.exception;

import com.tweakdapp.backend.shared.exception.NotFoundException;

import java.util.UUID;

/** The conversation does not exist — or the caller is not a participant, which is deliberately the same 404. */
public class DmConversationNotFoundException extends NotFoundException {

    public DmConversationNotFoundException(UUID conversationId) {
        super("Conversation not found: " + conversationId);
    }
}
