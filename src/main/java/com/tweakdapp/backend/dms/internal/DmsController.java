package com.tweakdapp.backend.dms.internal;

import com.tweakdapp.backend.dms.DmsService;
import com.tweakdapp.backend.dms.dto.DmConversationDto;
import com.tweakdapp.backend.dms.dto.DmConversationPageDto;
import com.tweakdapp.backend.dms.dto.DmMessagePageDto;
import com.tweakdapp.backend.dms.dto.DmReadReceiptDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * The read/state half of DMs, and the only way the Flutter client can see a conversation at all:
 * {@code authenticated} holds no privilege on any {@code dm_*} table, so the client cannot query
 * Supabase for DMs directly (verified 2026-08-31 — RLS on, zero policies, zero grants).
 *
 * <p><strong>Sending is not here.</strong> The client sends through the {@code SECURITY DEFINER}
 * RPC {@code dm_send_message}, which writes the message and its {@code type = 'dm'} notification in
 * one transaction — that notification row is what fires the DM push. {@code POST /messages} below
 * is retired for exactly that reason: it wrote a message and no notification, so anything sent
 * through it arrived silently. See the module README.
 *
 * <p>Live delivery is Supabase Realtime Broadcast (topic authorization: {@code dm_topic_is_peer}),
 * not the {@code /user/queue/dms} STOMP queue — nothing publishes to that queue any more, because
 * {@code DmEventPusher} only fires on writes that go through this JVM.
 */
@RestController
@RequestMapping("/api/v1/dms")
class DmsController {

    private static final Logger log = LoggerFactory.getLogger(DmsController.class);

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

    /**
     * One conversation by id — the peer, the last-message trio, unread count and presence.
     *
     * <p>Lets a chat screen open from an id alone (a push tap, a restored route) instead of having
     * to come through the list. Hidden conversations are returned; not-a-participant is the same
     * 404 as not-found.
     */
    @GetMapping("/conversations/{conversationId}")
    public DmConversationDto getConversation(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable UUID conversationId) {
        return dmsService.getConversation(UUID.fromString(jwt.getSubject()), conversationId);
    }

    /** One keyset page of a conversation's messages, newest first, plus the peer's read watermark. */
    @GetMapping("/conversations/{conversationId}/messages")
    public DmMessagePageDto getMessages(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable UUID conversationId,
                                        @RequestParam(required = false) String cursor,
                                        @RequestParam(defaultValue = "30") int size) {
        return dmsService.listMessages(UUID.fromString(jwt.getSubject()), conversationId, cursor, size);
    }

    /**
     * Retired 2026-08-31. Sending moved to the {@code dm_send_message} RPC, which also writes the
     * {@code dm} notification that triggers the push; this endpoint wrote the message only, so a
     * DM sent through it was delivered with no notification and no push.
     *
     * <p>Kept as an explicit 410 rather than deleted so a stale client gets a diagnosable answer
     * instead of a 404 indistinguishable from a bad path, and so this shows up in the logs if
     * anything still calls it. Verified unused before retiring: of the 10 DMs sent since the
     * 2026-08-30 migration, all 10 have a matching same-transaction {@code dm} notification row,
     * i.e. all 10 came through the RPC. {@code DmsService.sendMessage} is left intact — it is
     * still exercised by tests and is the reference implementation if sending ever comes back.
     */
    @PostMapping("/messages")
    public void sendMessage(@AuthenticationPrincipal Jwt jwt) {
        log.warn("Retired POST /api/v1/dms/messages called by {} — client should use the "
                + "dm_send_message RPC; this send did NOT happen", jwt.getSubject());
        throw new ResponseStatusException(HttpStatus.GONE,
                "Sending DMs through the backend is retired; use the dm_send_message RPC");
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
