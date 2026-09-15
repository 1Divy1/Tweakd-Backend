package com.tweakdapp.backend.posts.internal.repositories;

import java.util.UUID;

/**
 * One row of the global feed's keyset query: a post id and the score it ranked by — its
 * {@code ranking_score}, or the repost-boosted score when someone the viewer follows reposted it.
 * The score is what the next page's cursor resumes after.
 */
public interface RankedPostRow {

    UUID getId();

    double getScore();
}
