package com.carsocialmedia.backend.forums.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for editing a reply the caller authored. Only the text is editable; unlike a thread's
 * body, a reply's content is mandatory, so blank is rejected.
 *
 * @param content the new reply text (required, non-blank)
 */
public record UpdateReplyRequest(
        @NotBlank @Size(max = 20000) String content
) {}
