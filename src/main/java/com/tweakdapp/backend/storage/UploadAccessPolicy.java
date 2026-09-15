package com.tweakdapp.backend.storage;

import java.util.UUID;

/**
 * Decides whether a user may be handed an upload URL for a resource. Storage cannot depend on the
 * modules that own cars, posts or events, so each of them implements this for its {@link #target()}
 * and throws its own not-found / forbidden exception to deny.
 */
public interface UploadAccessPolicy {

    UploadTarget target();

    void requireUploadAccess(UUID userId, UUID resourceId);
}
