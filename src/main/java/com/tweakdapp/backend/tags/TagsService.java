package com.tweakdapp.backend.tags;

import com.tweakdapp.backend.tags.dto.TaggedContentKind;
import com.tweakdapp.backend.tags.dto.TaggedItemPageDto;

import java.util.UUID;

/**
 * The "tags" section of a profile — everywhere a user or one of their cars has been tagged, merged
 * into one chronological feed.
 *
 * <p>This module owns no data. It composes the four tagging surfaces ({@code posts} for posts and
 * comments, {@code forums} for threads and replies) plus {@code garage} for the user's car ids, in
 * the same way the {@code feed} module composes {@code posts}. Private DMs are deliberately out of
 * scope even though they can carry car tags.
 *
 * <p>Content the profile's owner wrote themselves is excluded: it already lives under their posts
 * tab, and the only way a car tag exists without its owner being tagged as a person is the author
 * tagging their own car.
 */
public interface TagsService {

    /**
     * One keyset page of the content the given user — or one of their cars — is tagged in, most
     * recently tagged first. All accounts are public, so any authenticated viewer may read any
     * profile's tags section.
     *
     * @param currentUserId the viewing user's UUID from the JWT subject (drives per-item viewer
     *        state such as {@code viewer_has_liked})
     * @param username the profile whose tags section to load
     * @param cursor opaque cursor from the previous page, or {@code null} for the first page
     * @param size max items to return (clamped to a sane maximum)
     * @return the page of tagged content plus the cursor for the next page (or {@code null} if last)
     * @throws com.tweakdapp.backend.profile.exception.ProfileNotFoundException if the username
     *         does not resolve to a profile
     * @throws com.tweakdapp.backend.tags.exception.InvalidTagCursorException if the cursor is unparseable
     */
    TaggedItemPageDto getTaggedContent(String currentUserId, String username, String cursor, int size);

    /**
     * One keyset page of the current user's own tags section — the same feed
     * {@link #getTaggedContent} returns for their username, without the username round-trip.
     */
    TaggedItemPageDto getMyTaggedContent(String currentUserId, String cursor, int size);

    /**
     * Removes the caller's own tags from one piece of content, which drops it out of their tags
     * section for every viewer.
     *
     * <p>The tag is deleted outright, not hidden: the content stops showing the caller among its
     * tagged people for everyone. Any of the caller's <em>own cars</em> tagged on the same content
     * go with it — leaving them would break the rule that a tagged car's owner is tagged too. Other
     * people's tags, including their cars, are untouched, and the content's author is free to tag
     * the caller again.
     *
     * <p>Idempotent: untagging content the caller is not tagged in is a no-op, not an error.
     *
     * @param currentUserId the untagging user's UUID from the JWT subject
     * @param kind which surface {@code targetId} refers to
     * @param targetId the post, comment, thread or reply to untag from
     * @throws com.tweakdapp.backend.shared.exception.NotFoundException if the content does not exist
     */
    void untagSelf(String currentUserId, TaggedContentKind kind, UUID targetId);
}
