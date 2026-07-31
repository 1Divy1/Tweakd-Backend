package com.carsocialmedia.backend.shared.tagging;

import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

/**
 * A pointer to one piece of content a user (or one of their cars) is tagged in, without any of the
 * content itself. The tag-owning modules ({@code posts}, {@code forums}) return these from their
 * keyset queries; the {@code tags} module merges the four streams into one chronological page and
 * only then asks each module to assemble the DTOs for the ids that actually made the page.
 *
 * <p>Kept in {@code shared} because two independent modules produce it and a third consumes it —
 * putting it in either producer would make the other depend on it.
 *
 * @param id the tagged content's id (post, comment, thread or reply)
 * @param parentId the container needed to render / deep-link the content — the post id for a
 *        comment, the thread id for a reply; {@code null} for top-level content (posts, threads)
 * @param taggedAt when the tag was created; the sort key of the merged feed. Falls back to the
 *        content's own creation time for legacy rows whose tag timestamp is null
 */
public record TaggedContentRef(UUID id, UUID parentId, Instant taggedAt) {

    /**
     * The feed's total order: newest tag first, id descending as the tiebreaker.
     *
     * <p>The id half deliberately compares <em>unsigned</em>, because that is how Postgres orders
     * {@code uuid} (byte-wise) while {@link UUID#compareTo} treats the halves as signed longs. The
     * SQL keyset predicates and this in-memory merge must agree on the ordering, or a row could be
     * skipped or repeated across pages when two tags share a timestamp.
     */
    public static final Comparator<TaggedContentRef> NEWEST_FIRST =
            Comparator.comparing(TaggedContentRef::taggedAt).reversed()
                    .thenComparing(TaggedContentRef::id, unsignedUuidOrder().reversed());

    /** Postgres-compatible (unsigned, byte-wise) {@code uuid} ordering. */
    public static Comparator<UUID> unsignedUuidOrder() {
        return (a, b) -> {
            int high = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
            return high != 0 ? high : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
        };
    }
}
