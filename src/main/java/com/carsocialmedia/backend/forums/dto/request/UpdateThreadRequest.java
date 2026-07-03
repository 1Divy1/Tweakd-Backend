package com.carsocialmedia.backend.forums.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for editing a thread the caller authored. Reddit-style: only the OP body is editable —
 * the title, car scoping, and topics are fixed at creation. Sending a blank string clears the body
 * (it is optional on a thread).
 *
 * @param content the new OP body (required field; blank clears it)
 */
public record UpdateThreadRequest(
        @NotNull @Size(max = 20000) String content
) {}
