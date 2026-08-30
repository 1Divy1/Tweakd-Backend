package com.tweakdapp.backend.posts.internal.repositories;

import java.time.Instant;
import java.util.UUID;

/**
 * Projection of "a piece of content this user is tagged in" — the id plus the moment the tag was
 * made. Shared by the person-tag and car-tag keyset queries so the service can merge their two
 * streams without converting between projection types.
 *
 * <p>{@code parentId} is only populated by the comment queries (the comment's post); it is
 * {@code null} for posts, which are top-level.
 */
public interface TagRefRow {

    UUID getTargetId();

    UUID getParentId();

    Instant getTaggedAt();
}
