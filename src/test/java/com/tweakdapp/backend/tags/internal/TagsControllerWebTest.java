package com.tweakdapp.backend.tags.internal;

import com.tweakdapp.backend.posts.dto.CommentDto;
import com.tweakdapp.backend.posts.dto.PostDto;
import com.tweakdapp.backend.tags.TagsService;
import com.tweakdapp.backend.tags.dto.TaggedContentKind;
import com.tweakdapp.backend.tags.dto.TaggedItemDto;
import com.tweakdapp.backend.tags.dto.TaggedItemPageDto;
import com.tweakdapp.backend.testsupport.AppWebMvcTest;
import com.tweakdapp.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The tags REST slice: authentication is required, a merged page serializes with the {@code kind}
 * discriminator and snake_case fields, unset content slots are omitted rather than sent as nulls,
 * the untag route parses its kind path segment (400 on an unknown one) and answers 204.
 */
@AppWebMvcTest(TagsController.class)
class TagsControllerWebTest {

    private static final UUID POST = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID COMMENT = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID COMMENT_POST = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final Instant TAGGED_AT = Instant.parse("2026-07-22T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TagsService tagsService;

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/tags/by-username/marius"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void serializesAPostItemWithoutTheUnusedContentSlots() throws Exception {
        when(tagsService.getTaggedContent(any(), eq("marius"), any(), anyInt()))
                .thenReturn(new TaggedItemPageDto(List.of(TaggedItemDto.post(TAGGED_AT, post(POST))), "CURSOR"));

        mockMvc.perform(get("/api/v1/tags/by-username/marius").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].kind").value("post"))
                .andExpect(jsonPath("$.items[0].tagged_at").exists())
                .andExpect(jsonPath("$.items[0].target_id").value(POST.toString()))
                .andExpect(jsonPath("$.items[0].post.id").value(POST.toString()))
                .andExpect(jsonPath("$.items[0].comment").doesNotExist())
                .andExpect(jsonPath("$.items[0].thread").doesNotExist())
                .andExpect(jsonPath("$.items[0].reply").doesNotExist())
                .andExpect(jsonPath("$.next_cursor").value("CURSOR"));
    }

    @Test
    void serializesACommentItemWithItsParentPost() throws Exception {
        when(tagsService.getTaggedContent(any(), any(), any(), anyInt()))
                .thenReturn(new TaggedItemPageDto(
                        List.of(TaggedItemDto.comment(TAGGED_AT, comment(COMMENT), post(COMMENT_POST))), null));

        mockMvc.perform(get("/api/v1/tags/by-username/marius").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].kind").value("post_comment"))
                .andExpect(jsonPath("$.items[0].target_id").value(COMMENT.toString()))
                .andExpect(jsonPath("$.items[0].comment.id").value(COMMENT.toString()))
                .andExpect(jsonPath("$.items[0].post.id").value(COMMENT_POST.toString()))
                .andExpect(jsonPath("$.next_cursor").doesNotExist());
    }

    @Test
    void passesTheCursorAndSizeThrough() throws Exception {
        when(tagsService.getTaggedContent(any(), any(), any(), anyInt()))
                .thenReturn(new TaggedItemPageDto(List.of(), null));

        mockMvc.perform(get("/api/v1/tags/by-username/marius")
                        .param("cursor", "TOKEN").param("size", "5").with(TestJwts.user()))
                .andExpect(status().isOk());

        verify(tagsService).getTaggedContent(TestJwts.USER_ID.toString(), "marius", "TOKEN", 5);
    }

    @Test
    void meEndpointReadsTheCallersOwnFeed() throws Exception {
        when(tagsService.getMyTaggedContent(any(), any(), anyInt()))
                .thenReturn(new TaggedItemPageDto(List.of(), null));

        mockMvc.perform(get("/api/v1/tags/me").with(TestJwts.user()))
                .andExpect(status().isOk());

        verify(tagsService).getMyTaggedContent(TestJwts.USER_ID.toString(), null, 20);
    }

    @Test
    void untagAnswersNoContentAndForwardsTheParsedKind() throws Exception {
        mockMvc.perform(delete("/api/v1/tags/forum_reply/" + POST).with(TestJwts.user()))
                .andExpect(status().isNoContent());

        verify(tagsService).untagSelf(TestJwts.USER_ID.toString(), TaggedContentKind.FORUM_REPLY, POST);
    }

    @Test
    void untagRejectsAnUnknownKindWithoutReachingTheService() throws Exception {
        mockMvc.perform(delete("/api/v1/tags/dm/" + POST).with(TestJwts.user()))
                .andExpect(status().isBadRequest());

        verify(tagsService, never()).untagSelf(any(), any(), any());
    }

    // ---- fixtures -----------------------------------------------------------

    private static PostDto post(UUID id) {
        return new PostDto(id, "caption", null, List.of(), List.of(), List.of(),
                0, 0, 0, 0, true, true, true, true, false, false, TAGGED_AT, TAGGED_AT, null);
    }

    private static CommentDto comment(UUID id) {
        return new CommentDto(id, null, "text", List.of(), List.of(), null, false, 0, false, TAGGED_AT, 0);
    }


}
