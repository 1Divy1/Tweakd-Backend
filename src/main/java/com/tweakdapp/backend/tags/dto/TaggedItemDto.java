package com.tweakdapp.backend.tags.dto;

import com.tweakdapp.backend.forums.dto.ReplyDto;
import com.tweakdapp.backend.forums.dto.ThreadCardDto;
import com.tweakdapp.backend.posts.dto.CommentDto;
import com.tweakdapp.backend.posts.dto.PostDto;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of a profile's tags feed: a piece of content the profile's owner (or one of their cars)
 * is tagged in, in the same shape the app already renders that content elsewhere.
 *
 * <p>Exactly one of {@code post} / {@code thread} is always set — it is either the tagged content
 * itself or, for a comment or reply, the container it belongs to. {@code comment} and {@code reply}
 * are set only for their own kinds. Unset fields are omitted from the JSON, so a post item is just
 * <code>{kind, tagged_at, target_id, post}</code>.
 *
 * @param kind which surface the tag lives on; the client's rendering discriminator
 * @param taggedAt when the tag was made — the feed's sort key, not the content's creation time
 * @param targetId the id of the tagged content itself (the post, comment, thread or reply), which
 *        is what the untag endpoint takes
 * @param post the tagged post, or the post a tagged comment belongs to; {@code null} for forum kinds
 * @param comment the tagged comment; only set for {@code post_comment}
 * @param thread the tagged thread, or the thread a tagged reply belongs to; {@code null} for post kinds
 * @param reply the tagged reply; only set for {@code forum_reply}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaggedItemDto(
        TaggedContentKind kind,
        Instant taggedAt,
        UUID targetId,
        PostDto post,
        CommentDto comment,
        ThreadCardDto thread,
        ReplyDto reply
) {

    public static TaggedItemDto post(Instant taggedAt, PostDto post) {
        return new TaggedItemDto(TaggedContentKind.POST, taggedAt, post.id(), post, null, null, null);
    }

    public static TaggedItemDto comment(Instant taggedAt, CommentDto comment, PostDto post) {
        return new TaggedItemDto(TaggedContentKind.POST_COMMENT, taggedAt, comment.id(), post, comment, null, null);
    }

    public static TaggedItemDto thread(Instant taggedAt, ThreadCardDto thread) {
        return new TaggedItemDto(TaggedContentKind.FORUM_THREAD, taggedAt, thread.id(), null, null, thread, null);
    }

    public static TaggedItemDto reply(Instant taggedAt, ReplyDto reply, ThreadCardDto thread) {
        return new TaggedItemDto(TaggedContentKind.FORUM_REPLY, taggedAt, reply.id(), null, null, thread, reply);
    }
}
