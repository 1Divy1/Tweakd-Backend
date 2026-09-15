package com.tweakdapp.backend.posts.internal.repositories;

import java.util.UUID;

/** One repost: which post, and who reposted it. */
public interface RepostRow {

    UUID getPostId();

    UUID getUserId();
}
