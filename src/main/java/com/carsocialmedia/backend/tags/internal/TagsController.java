package com.carsocialmedia.backend.tags.internal;

import com.carsocialmedia.backend.tags.TagsService;
import com.carsocialmedia.backend.tags.dto.TaggedContentKind;
import com.carsocialmedia.backend.tags.dto.TaggedItemPageDto;
import com.carsocialmedia.backend.tags.exception.UnknownTaggedContentKindException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tags")
public class TagsController {

    private final TagsService tagsService;

    public TagsController(TagsService tagsService) {
        this.tagsService = tagsService;
    }

    /**
     * The "tags" section of a profile: one keyset page of everything that user (or one of their
     * cars) is tagged in — posts, post comments, forum threads and forum replies — most recently
     * tagged first. The client echoes {@code next_cursor} back as {@code ?cursor=} to page on.
     */
    @GetMapping("/by-username/{username}")
    public TaggedItemPageDto getUserTags(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable String username,
                                         @RequestParam(required = false) String cursor,
                                         @RequestParam(defaultValue = "20") int size) {
        return tagsService.getTaggedContent(jwt.getSubject(), username, cursor, size);
    }

    /** The caller's own tags section — same feed, no username round-trip. */
    @GetMapping("/me")
    public TaggedItemPageDto getMyTags(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam(required = false) String cursor,
                                       @RequestParam(defaultValue = "20") int size) {
        return tagsService.getMyTaggedContent(jwt.getSubject(), cursor, size);
    }

    /**
     * Removes the caller's tags from one piece of content, dropping it out of their tags section
     * for everyone. Their own cars tagged on the same content go with it. Idempotent.
     *
     * @param kind one of {@code post}, {@code post_comment}, {@code forum_thread}, {@code forum_reply}
     * @param targetId the post / comment / thread / reply to untag from
     */
    @DeleteMapping("/{kind}/{targetId}")
    public ResponseEntity<Void> untagSelf(@AuthenticationPrincipal Jwt jwt,
                                          @PathVariable String kind,
                                          @PathVariable UUID targetId) {
        TaggedContentKind parsed = TaggedContentKind.fromWireName(kind);
        if (parsed == null) {
            throw new UnknownTaggedContentKindException(kind);
        }
        tagsService.untagSelf(jwt.getSubject(), parsed, targetId);
        return ResponseEntity.noContent().build();
    }
}
