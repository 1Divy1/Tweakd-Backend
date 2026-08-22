package com.tweakdapp.backend.shared.moderation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A content module's answer to "show me the reported thing": the minimal, uniform shape the admin
 * module needs to render a moderation case and to snapshot the content into the audit log before a
 * hard delete. Every reportable content type (post, comment, forum thread, forum reply) maps to
 * this one record so the moderation queue does not need a per-type DTO.
 *
 * @param targetId the content's id
 * @param authorId the content author's profile id
 * @param content the text body — a post's caption, a comment/reply's text, or a thread's
 *        {@code title + "\n\n" + body}
 * @param mediaUrls resolved public URLs of attached media (post images); empty for text-only types
 * @param createdAt when the content was created
 */
public record ModerationContentDto(
        UUID targetId,
        UUID authorId,
        String content,
        List<String> mediaUrls,
        Instant createdAt) {
}
