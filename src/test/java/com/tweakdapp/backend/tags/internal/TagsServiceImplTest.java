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
import com.tweakdapp.backend.tags.dto.TaggedContentKind;
import com.tweakdapp.backend.tags.dto.TaggedItemDto;
import com.tweakdapp.backend.tags.dto.TaggedItemPageDto;
import com.tweakdapp.backend.tags.exception.InvalidTagCursorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * The merge policy behind a profile's "tags" section: four independent keyset streams (posts,
 * comments, threads, replies) folded into one chronological page, hydrated with one batch call per
 * content type, and paged by a single cursor that every stream re-applies.
 */
class TagsServiceImplTest {

    private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CAR = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private static final UUID POST = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID COMMENT = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID COMMENT_POST = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID THREAD = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID REPLY = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID REPLY_THREAD = UUID.fromString("00000000-0000-0000-0000-0000000000e2");

    private static final Instant T1 = Instant.parse("2026-07-20T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-07-21T10:00:00Z");
    private static final Instant T3 = Instant.parse("2026-07-22T10:00:00Z");
    private static final Instant T4 = Instant.parse("2026-07-23T10:00:00Z");

    private PostsService postsService;
    private ForumsService forumsService;
    private GarageService garageService;
    private ProfileService profileService;
    private TagsServiceImpl service;

    @BeforeEach
    void setUp() {
        postsService = mock(PostsService.class);
        forumsService = mock(ForumsService.class);
        garageService = mock(GarageService.class);
        profileService = mock(ProfileService.class);
        service = new TagsServiceImpl(postsService, forumsService, garageService, profileService);

        when(profileService.findIdByUsername("owner")).thenReturn(Optional.of(OWNER));
        when(garageService.findCarIdsByOwner(OWNER)).thenReturn(List.of(CAR));

        // Default: every stream empty; individual tests fill in the ones they care about.
        when(postsService.findTaggedPostRefs(any(), any(), any(), any(), anyInt())).thenReturn(List.of());
        when(postsService.findTaggedCommentRefs(any(), any(), any(), any(), anyInt())).thenReturn(List.of());
        when(forumsService.findTaggedThreadRefs(any(), any(), any(), any(), anyInt())).thenReturn(List.of());
        when(forumsService.findTaggedReplyRefs(any(), any(), any(), any(), anyInt())).thenReturn(List.of());
        when(postsService.getPostsByIds(any(), any())).thenReturn(List.of());
        when(postsService.getCommentsByIds(any(), any())).thenReturn(List.of());
        when(forumsService.getThreadCardsByIds(any(), any())).thenReturn(List.of());
        when(forumsService.getRepliesByIds(any(), any())).thenReturn(List.of());
    }

    // ---- merging ------------------------------------------------------------

    @Test
    void mergesTheFourStreamsIntoOneFeedNewestTagFirst() {
        givenAllFourStreams();

        TaggedItemPageDto page = service.getTaggedContent(VIEWER.toString(), "owner", null, 20);

        assertThat(page.items()).extracting(TaggedItemDto::kind).containsExactly(
                TaggedContentKind.FORUM_REPLY,     // T4
                TaggedContentKind.FORUM_THREAD,    // T3
                TaggedContentKind.POST_COMMENT,    // T2
                TaggedContentKind.POST);           // T1
        assertThat(page.items()).extracting(TaggedItemDto::taggedAt).containsExactly(T4, T3, T2, T1);
        assertThat(page.items()).extracting(TaggedItemDto::targetId)
                .containsExactly(REPLY, THREAD, COMMENT, POST);
    }

    @Test
    void carriesTheParentPostOfACommentAndTheParentThreadOfAReply() {
        givenAllFourStreams();

        List<TaggedItemDto> items = service.getTaggedContent(VIEWER.toString(), "owner", null, 20).items();

        TaggedItemDto comment = items.stream().filter(i -> i.kind() == TaggedContentKind.POST_COMMENT)
                .findFirst().orElseThrow();
        assertThat(comment.comment().id()).isEqualTo(COMMENT);
        assertThat(comment.post().id()).isEqualTo(COMMENT_POST);
        assertThat(comment.thread()).isNull();

        TaggedItemDto reply = items.stream().filter(i -> i.kind() == TaggedContentKind.FORUM_REPLY)
                .findFirst().orElseThrow();
        assertThat(reply.reply().id()).isEqualTo(REPLY);
        assertThat(reply.thread().id()).isEqualTo(REPLY_THREAD);
        assertThat(reply.post()).isNull();
    }

    @Test
    void loadsACommentsParentPostInTheSameBatchAsTheTaggedPosts() {
        when(postsService.findTaggedPostRefs(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(new TaggedContentRef(POST, null, T1)));
        when(postsService.findTaggedCommentRefs(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(new TaggedContentRef(COMMENT, COMMENT_POST, T2)));
        when(postsService.getPostsByIds(eq(VIEWER), any()))
                .thenReturn(List.of(post(POST), post(COMMENT_POST)));
        when(postsService.getCommentsByIds(eq(VIEWER), any())).thenReturn(List.of(comment(COMMENT)));

        service.getTaggedContent(VIEWER.toString(), "owner", null, 20);

        verify(postsService).getPostsByIds(VIEWER, List.of(POST, COMMENT_POST));
        verify(postsService, times(1)).getPostsByIds(any(), any());
    }

    @Test
    void dropsAnItemWhoseContentOrParentNoLongerResolves() {
        when(postsService.findTaggedCommentRefs(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(new TaggedContentRef(COMMENT, COMMENT_POST, T2)));
        when(postsService.getCommentsByIds(any(), any())).thenReturn(List.of(comment(COMMENT)));
        // The parent post was deleted between the ref query and the hydration.
        when(postsService.getPostsByIds(any(), any())).thenReturn(List.of());

        assertThat(service.getTaggedContent(VIEWER.toString(), "owner", null, 20).items()).isEmpty();
    }

    // ---- paging -------------------------------------------------------------

    @Test
    void trimsToTheRequestedSizeAndReturnsACursorAtTheBoundary() {
        givenAllFourStreams();

        TaggedItemPageDto page = service.getTaggedContent(VIEWER.toString(), "owner", null, 2);

        assertThat(page.items()).extracting(TaggedItemDto::targetId).containsExactly(REPLY, THREAD);
        assertThat(page.nextCursor()).isNotNull();

        // The cursor is the last item of the page, so the next request resumes right after it.
        TaggedItemPageDto next = service.getTaggedContent(VIEWER.toString(), "owner", page.nextCursor(), 2);
        assertThat(next).isNotNull();
        verify(postsService).findTaggedPostRefs(OWNER, List.of(CAR), T3, THREAD, 3);
        verify(postsService).findTaggedCommentRefs(OWNER, List.of(CAR), T3, THREAD, 3);
        verify(forumsService).findTaggedThreadRefs(OWNER, List.of(CAR), T3, THREAD, 3);
        verify(forumsService).findTaggedReplyRefs(OWNER, List.of(CAR), T3, THREAD, 3);
    }

    @Test
    void returnsNoCursorWhenTheFeedIsExhausted() {
        givenAllFourStreams();

        TaggedItemPageDto page = service.getTaggedContent(VIEWER.toString(), "owner", null, 20);

        assertThat(page.items()).hasSize(4);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void returnsAnEmptyPageWithoutHydratingAnything() {
        TaggedItemPageDto page = service.getTaggedContent(VIEWER.toString(), "owner", null, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        verify(postsService, never()).getPostsByIds(any(), any());
        verify(forumsService, never()).getThreadCardsByIds(any(), any());
    }

    @Test
    void overFetchesOneRefPerStreamAndClampsThePageSize() {
        service.getTaggedContent(VIEWER.toString(), "owner", null, 5000);

        verify(postsService).findTaggedPostRefs(OWNER, List.of(CAR), null, null, 51);
        verify(forumsService).findTaggedReplyRefs(OWNER, List.of(CAR), null, null, 51);
    }

    @Test
    void rejectsAnUnparseableCursor() {
        assertThatThrownBy(() -> service.getTaggedContent(VIEWER.toString(), "owner", "not-a-cursor", 20))
                .isInstanceOf(InvalidTagCursorException.class);
    }

    // ---- scoping ------------------------------------------------------------

    @Test
    void resolvesTheProfileOwnersCarsOnceAndSharesThemAcrossEveryStream() {
        service.getTaggedContent(VIEWER.toString(), "owner", null, 20);

        verify(garageService, times(1)).findCarIdsByOwner(OWNER);
        verify(postsService).findTaggedPostRefs(eq(OWNER), eq(List.of(CAR)), any(), any(), anyInt());
        verify(postsService).findTaggedCommentRefs(eq(OWNER), eq(List.of(CAR)), any(), any(), anyInt());
        verify(forumsService).findTaggedThreadRefs(eq(OWNER), eq(List.of(CAR)), any(), any(), anyInt());
        verify(forumsService).findTaggedReplyRefs(eq(OWNER), eq(List.of(CAR)), any(), any(), anyInt());
    }

    @Test
    void hydratesWithTheViewersIdSoLikeAndSaveFlagsAreTheViewers() {
        when(postsService.findTaggedPostRefs(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(new TaggedContentRef(POST, null, T1)));
        when(postsService.getPostsByIds(any(), any())).thenReturn(List.of(post(POST)));

        service.getTaggedContent(VIEWER.toString(), "owner", null, 20);

        verify(postsService).getPostsByIds(VIEWER, List.of(POST));
    }

    @Test
    void myTagsReadsTheCallersOwnFeedWithoutAUsernameLookup() {
        service.getMyTaggedContent(OWNER.toString(), null, 20);

        verify(postsService).findTaggedPostRefs(eq(OWNER), any(), any(), any(), anyInt());
        verify(profileService, never()).findIdByUsername(any());
    }

    @Test
    void unknownUsernameIsNotFound() {
        when(profileService.findIdByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTaggedContent(VIEWER.toString(), "ghost", null, 20))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    // ---- untagging ----------------------------------------------------------

    @Test
    void untagSelfRoutesEachKindToTheModuleThatOwnsIt() {
        service.untagSelf(VIEWER.toString(), TaggedContentKind.POST, POST);
        service.untagSelf(VIEWER.toString(), TaggedContentKind.POST_COMMENT, COMMENT);
        service.untagSelf(VIEWER.toString(), TaggedContentKind.FORUM_THREAD, THREAD);
        service.untagSelf(VIEWER.toString(), TaggedContentKind.FORUM_REPLY, REPLY);

        verify(postsService).removeSelfTagsFromPost(VIEWER, POST);
        verify(postsService).removeSelfTagsFromComment(VIEWER, COMMENT);
        verify(forumsService).removeSelfTagsFromThread(VIEWER, THREAD);
        verify(forumsService).removeSelfTagsFromReply(VIEWER, REPLY);
        verifyNoMoreInteractions(postsService, forumsService);
    }

    // ---- fixtures -----------------------------------------------------------

    /** One tagged item on each of the four surfaces, tagged T1 (post) through T4 (reply). */
    private void givenAllFourStreams() {
        when(postsService.findTaggedPostRefs(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(new TaggedContentRef(POST, null, T1)));
        when(postsService.findTaggedCommentRefs(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(new TaggedContentRef(COMMENT, COMMENT_POST, T2)));
        when(forumsService.findTaggedThreadRefs(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(new TaggedContentRef(THREAD, null, T3)));
        when(forumsService.findTaggedReplyRefs(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(new TaggedContentRef(REPLY, REPLY_THREAD, T4)));

        when(postsService.getPostsByIds(any(), any())).thenReturn(List.of(post(POST), post(COMMENT_POST)));
        when(postsService.getCommentsByIds(any(), any())).thenReturn(List.of(comment(COMMENT)));
        when(forumsService.getThreadCardsByIds(any(), any()))
                .thenReturn(List.of(thread(THREAD), thread(REPLY_THREAD)));
        when(forumsService.getRepliesByIds(any(), any())).thenReturn(List.of(reply(REPLY)));
    }

    private static PostDto post(UUID id) {
        return new PostDto(id, "caption", null, List.of(), List.of(), List.of(),
                0, 0, 0, 0, true, true, true, true, false, false, T1, T1);
    }

    private static CommentDto comment(UUID id) {
        return new CommentDto(id, null, "text", List.of(), List.of(), null, false, 0, false, T1, 0);
    }

    private static ThreadCardDto thread(UUID id) {
        return new ThreadCardDto(id, "title", null, null, null, List.of(), List.of(), List.of(),
                0, 0, T1, false, false, false, false);
    }

    private static ReplyDto reply(UUID id) {
        return new ReplyDto(id, null, "text", List.of(), List.of(), 0, 0, false, false, false, T1);
    }
}
