package com.tweakdapp.backend.tags.internal;

import com.tweakdapp.backend.forums.ForumsService;
import com.tweakdapp.backend.forums.dto.ReplyDto;
import com.tweakdapp.backend.forums.dto.ThreadCardDto;
import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.posts.PostsService;
import com.tweakdapp.backend.posts.dto.CommentDto;
import com.tweakdapp.backend.posts.dto.PostDto;
import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.profile.exception.ProfileNotFoundException;
import com.tweakdapp.backend.shared.tagging.TaggedContentRef;
import com.tweakdapp.backend.tags.TagsService;
import com.tweakdapp.backend.tags.dto.TaggedContentKind;
import com.tweakdapp.backend.tags.dto.TaggedItemDto;
import com.tweakdapp.backend.tags.dto.TaggedItemPageDto;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TagsServiceImpl implements TagsService {

    /** Hard cap on page size so a client can't request an unbounded page. */
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final PostsService postsService;
    private final ForumsService forumsService;
    private final GarageService garageService;
    private final ProfileService profileService;

    public TagsServiceImpl(PostsService postsService,
                           ForumsService forumsService,
                           GarageService garageService,
                           ProfileService profileService) {
        this.postsService = postsService;
        this.forumsService = forumsService;
        this.garageService = garageService;
        this.profileService = profileService;
    }

    @Override
    public TaggedItemPageDto getTaggedContent(String currentUserId, String username, String cursor, int size) {
        UUID ownerId = profileService.findIdByUsername(username)
                .orElseThrow(() -> ProfileNotFoundException.byUsername(username));
        return taggedPage(UUID.fromString(currentUserId), ownerId, cursor, size);
    }

    @Override
    public TaggedItemPageDto getMyTaggedContent(String currentUserId, String cursor, int size) {
        UUID userId = UUID.fromString(currentUserId);
        return taggedPage(userId, userId, cursor, size);
    }

    @Override
    public void untagSelf(String currentUserId, TaggedContentKind kind, UUID targetId) {
        UUID userId = UUID.fromString(currentUserId);
        switch (kind) {
            case POST -> postsService.removeSelfTagsFromPost(userId, targetId);
            case POST_COMMENT -> postsService.removeSelfTagsFromComment(userId, targetId);
            case FORUM_THREAD -> forumsService.removeSelfTagsFromThread(userId, targetId);
            case FORUM_REPLY -> forumsService.removeSelfTagsFromReply(userId, targetId);
        }
    }

    /**
     * Builds one page of the merged feed in two phases: first ask each of the four surfaces for a
     * page of lightweight refs and merge them, then hydrate only the refs that survived the merge.
     * Content is never assembled for items that fall off the end of the page.
     */
    private TaggedItemPageDto taggedPage(UUID viewerId, UUID ownerId, String cursor, int size) {
        int limit = clampSize(size);
        TagCursor from = TagCursor.decode(cursor);
        Instant cursorTs = from == null ? null : from.taggedAt();
        UUID cursorId = from == null ? null : from.targetId();

        // One extra ref per stream tells us whether a further page exists; the owner's car ids are
        // resolved once and reused by all four.
        int fetch = limit + 1;
        List<UUID> ownedCarIds = garageService.findCarIdsByOwner(ownerId);

        List<Entry> merged = new ArrayList<>();
        collect(merged, TaggedContentKind.POST,
                postsService.findTaggedPostRefs(ownerId, ownedCarIds, cursorTs, cursorId, fetch));
        collect(merged, TaggedContentKind.POST_COMMENT,
                postsService.findTaggedCommentRefs(ownerId, ownedCarIds, cursorTs, cursorId, fetch));
        collect(merged, TaggedContentKind.FORUM_THREAD,
                forumsService.findTaggedThreadRefs(ownerId, ownedCarIds, cursorTs, cursorId, fetch));
        collect(merged, TaggedContentKind.FORUM_REPLY,
                forumsService.findTaggedReplyRefs(ownerId, ownedCarIds, cursorTs, cursorId, fetch));

        merged.sort(Comparator.comparing(Entry::ref, TaggedContentRef.NEWEST_FIRST));

        // Every stream was capped at limit + 1, so an over-long merge is the only way more items
        // can exist — and it always means they do.
        boolean hasMore = merged.size() > limit;
        List<Entry> page = hasMore ? merged.subList(0, limit) : merged;
        if (page.isEmpty()) {
            return new TaggedItemPageDto(List.of(), null);
        }

        List<TaggedItemDto> items = hydrate(viewerId, page);
        TaggedContentRef lastRef = page.getLast().ref();
        String nextCursor = hasMore ? new TagCursor(lastRef.taggedAt(), lastRef.id()).encode() : null;
        return new TaggedItemPageDto(items, nextCursor);
    }

    /**
     * Turns the page's refs into rendered items with one batch call per content type — comments and
     * their parent posts share a single post lookup, as do replies and their parent threads.
     *
     * <p>An item whose content (or whose parent) no longer resolves is dropped: the feed is
     * assembled from ids read a moment earlier, and content deleted in between should simply not
     * appear rather than fail the whole page.
     */
    private List<TaggedItemDto> hydrate(UUID viewerId, List<Entry> page) {
        List<UUID> postIds = idsOf(page, TaggedContentKind.POST, Entry::targetId);
        postIds.addAll(idsOf(page, TaggedContentKind.POST_COMMENT, Entry::parentId));
        List<UUID> commentIds = idsOf(page, TaggedContentKind.POST_COMMENT, Entry::targetId);
        List<UUID> threadIds = idsOf(page, TaggedContentKind.FORUM_THREAD, Entry::targetId);
        threadIds.addAll(idsOf(page, TaggedContentKind.FORUM_REPLY, Entry::parentId));
        List<UUID> replyIds = idsOf(page, TaggedContentKind.FORUM_REPLY, Entry::targetId);

        Map<UUID, PostDto> posts = index(postsService.getPostsByIds(viewerId, distinct(postIds)), PostDto::id);
        Map<UUID, CommentDto> comments = index(postsService.getCommentsByIds(viewerId, commentIds), CommentDto::id);
        Map<UUID, ThreadCardDto> threads =
                index(forumsService.getThreadCardsByIds(viewerId, distinct(threadIds)), ThreadCardDto::id);
        Map<UUID, ReplyDto> replies = index(forumsService.getRepliesByIds(viewerId, replyIds), ReplyDto::id);

        List<TaggedItemDto> items = new ArrayList<>(page.size());
        for (Entry entry : page) {
            Instant taggedAt = entry.ref().taggedAt();
            switch (entry.kind()) {
                case POST -> {
                    PostDto post = posts.get(entry.targetId());
                    if (post != null) {
                        items.add(TaggedItemDto.post(taggedAt, post));
                    }
                }
                case POST_COMMENT -> {
                    CommentDto comment = comments.get(entry.targetId());
                    PostDto post = posts.get(entry.parentId());
                    if (comment != null && post != null) {
                        items.add(TaggedItemDto.comment(taggedAt, comment, post));
                    }
                }
                case FORUM_THREAD -> {
                    ThreadCardDto thread = threads.get(entry.targetId());
                    if (thread != null) {
                        items.add(TaggedItemDto.thread(taggedAt, thread));
                    }
                }
                case FORUM_REPLY -> {
                    ReplyDto reply = replies.get(entry.targetId());
                    ThreadCardDto thread = threads.get(entry.parentId());
                    if (reply != null && thread != null) {
                        items.add(TaggedItemDto.reply(taggedAt, reply, thread));
                    }
                }
            }
        }
        return List.copyOf(items);
    }

    private static void collect(List<Entry> target, TaggedContentKind kind, List<TaggedContentRef> refs) {
        refs.forEach(ref -> target.add(new Entry(kind, ref)));
    }

    private static List<UUID> idsOf(List<Entry> page, TaggedContentKind kind, Function<Entry, UUID> id) {
        return page.stream()
                .filter(e -> e.kind() == kind)
                .map(id)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** Distinct, order-preserving — a comment's parent post may also be tagged in its own right. */
    private static List<UUID> distinct(List<UUID> ids) {
        return List.copyOf(new LinkedHashSet<>(ids));
    }

    private static <T> Map<UUID, T> index(List<T> values, Function<T, UUID> id) {
        return values.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a));
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /** One merged-feed row before hydration: which surface it came from, and its ref. */
    private record Entry(TaggedContentKind kind, TaggedContentRef ref) {

        UUID targetId() {
            return ref.id();
        }

        UUID parentId() {
            return ref.parentId();
        }
    }
}
