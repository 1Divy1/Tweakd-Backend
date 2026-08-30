package com.tweakdapp.backend.dms.internal;

import com.tweakdapp.backend.dms.DmsService;
import com.tweakdapp.backend.dms.dto.DmConversationPageDto;
import com.tweakdapp.backend.dms.dto.DmMessageDto;
import com.tweakdapp.backend.dms.dto.DmMessagePageDto;
import com.tweakdapp.backend.dms.dto.DmReadReceiptDto;
import com.tweakdapp.backend.dms.dto.SendMessageRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * The durable half of DMs. Everything live (delivery, read ticks, deletions, typing) rides the
 * {@code /user/queue/dms} WebSocket queue instead — see {@code DmEventPusher} /
 * {@code DmsTypingController}.
 */
@RestController
@RequestMapping("/api/v1/dms")
class DmsController {

    private final DmsService dmsService;

    DmsController(DmsService dmsService) {
        this.dmsService = dmsService;
    }

    /** One keyset page of the caller's chats list, most recently active first. */
    @GetMapping("/conversations")
    public DmConversationPageDto getConversations(@AuthenticationPrincipal Jwt jwt,
                                                  @RequestParam(required = false) String cursor,
                                                  @RequestParam(defaultValue = "20") int size) {
        return dmsService.listConversations(UUID.fromString(jwt.getSubject()), cursor, size);
    }

    /** One keyset page of a conversation's messages, newest first, plus the peer's read watermark. */
    @GetMapping("/conversations/{conversationId}/messages")
    public DmMessagePageDto getMessages(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable UUID conversationId,
                                        @RequestParam(required = false) String cursor,
                                        @RequestParam(defaultValue = "30") int size) {
        return dmsService.listMessages(UUID.fromString(jwt.getSubject()), conversationId, cursor, size);
    }

    /** Sends a DM; the pair's conversation is created implicitly on their first message. */
    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public DmMessageDto sendMessage(@AuthenticationPrincipal Jwt jwt,
                                    @Valid @RequestBody SendMessageRequest request) {
        return dmsService.sendMessage(UUID.fromString(jwt.getSubject()), request);
    }

    /** Marks the conversation read up to its latest message; returns the new watermark. */
    @PostMapping("/conversations/{conversationId}/read")
    public DmReadReceiptDto markRead(@AuthenticationPrincipal Jwt jwt,
                                     @PathVariable UUID conversationId) {
        return dmsService.markRead(UUID.fromString(jwt.getSubject()), conversationId);
    }

    /** Hides the conversation from the caller's chats list; the peer's side is untouched. */
    @PostMapping("/conversations/{conversationId}/hide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hideConversation(@AuthenticationPrincipal Jwt jwt,
                                 @PathVariable UUID conversationId) {
        dmsService.hideConversation(UUID.fromString(jwt.getSubject()), conversationId);
    }

    /** Soft-deletes one of the caller's own messages (idempotent). */
    @DeleteMapping("/messages/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMessage(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable UUID messageId) {
        dmsService.deleteMessage(UUID.fromString(jwt.getSubject()), messageId);
    }

    /** Total unread DMs for the app badge. */
    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("unread", dmsService.countUnread(UUID.fromString(jwt.getSubject())));
    }
}
