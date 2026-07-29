package com.carsocialmedia.backend.notification.internal;

import com.carsocialmedia.backend.forums.events.ForumReplyLikedEvent;
import com.carsocialmedia.backend.forums.events.ForumReplyRepliedEvent;
import com.carsocialmedia.backend.forums.events.ForumReplyTaggedEvent;
import com.carsocialmedia.backend.forums.events.ForumThreadLikedEvent;
import com.carsocialmedia.backend.forums.events.ForumThreadRepliedEvent;
import com.carsocialmedia.backend.forums.events.ForumThreadTaggedEvent;
import com.carsocialmedia.backend.notification.NotificationService;
import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.profile.dto.NotificationPreferencesDto;
import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns {@code forums} engagement events into in-app notifications for the thread / reply author.
 * Each handler runs asynchronously, in its own new transaction, after the producing transaction
 * commits ({@code @Async @Transactional(REQUIRES_NEW) @TransactionalEventListener} — the
 * standard-Spring equivalent of Modulith's {@code @ApplicationModuleListener}, which isn't on the
 * classpath here), so a rolled-back reply/like never notifies. The self-notify and anonymized-author
 * skips already happened at the publish site; here we only gate on the recipient's preferences and
 * resolve the actor's username. Replies gate on {@code comments_enabled}; likes on
 * {@code likes_enabled}; tags on {@code tags_enabled}.
 */
@Component
class ForumsNotificationListener {

    private final ProfileService profileService;
    private final NotificationService notificationService;

    ForumsNotificationListener(ProfileService profileService, NotificationService notificationService) {
        this.profileService = profileService;
        this.notificationService = notificationService;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(ForumThreadRepliedEvent event) {
        NotificationPreferencesDto prefs = profileService.getNotificationPreferencesOrDefault(event.recipientId());
        if (!prefs.commentsEnabled()) {
            return;
        }
        String username = resolveUsername(event.actorId());
        Map<String, Object> payload = basePayload(event.actorId(), username);
        payload.put("thread_id", event.threadId().toString());
        payload.put("reply_id", event.replyId().toString());
        notificationService.push(
                event.recipientId(),
                "forum_thread_reply",
                username + " replied to your thread",
                event.excerpt(),
                payload);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(ForumReplyRepliedEvent event) {
        NotificationPreferencesDto prefs = profileService.getNotificationPreferencesOrDefault(event.recipientId());
        if (!prefs.commentsEnabled()) {
            return;
        }
        String username = resolveUsername(event.actorId());
        Map<String, Object> payload = basePayload(event.actorId(), username);
        payload.put("thread_id", event.threadId().toString());
        payload.put("parent_reply_id", event.parentReplyId().toString());
        payload.put("reply_id", event.replyId().toString());
        notificationService.push(
                event.recipientId(),
                "forum_reply_reply",
                username + " replied to your reply",
                event.excerpt(),
                payload);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(ForumThreadLikedEvent event) {
        NotificationPreferencesDto prefs = profileService.getNotificationPreferencesOrDefault(event.recipientId());
        if (!prefs.likesEnabled()) {
            return;
        }
        String username = resolveUsername(event.actorId());
        Map<String, Object> payload = basePayload(event.actorId(), username);
        payload.put("thread_id", event.threadId().toString());
        notificationService.push(
                event.recipientId(),
                "forum_thread_like",
                username + " liked your thread",
                null,
                payload);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(ForumReplyLikedEvent event) {
        NotificationPreferencesDto prefs = profileService.getNotificationPreferencesOrDefault(event.recipientId());
        if (!prefs.likesEnabled()) {
            return;
        }
        String username = resolveUsername(event.actorId());
        Map<String, Object> payload = basePayload(event.actorId(), username);
        payload.put("thread_id", event.threadId().toString());
        payload.put("reply_id", event.replyId().toString());
        notificationService.push(
                event.recipientId(),
                "forum_reply_like",
                username + " liked your reply",
                null,
                payload);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(ForumThreadTaggedEvent event) {
        NotificationPreferencesDto prefs = profileService.getNotificationPreferencesOrDefault(event.recipientId());
        if (!prefs.tagsEnabled()) {
            return;
        }
        String username = resolveUsername(event.actorId());
        Map<String, Object> payload = basePayload(event.actorId(), username);
        payload.put("thread_id", event.threadId().toString());
        payload.put("car_tagged", event.carTagged());
        notificationService.push(
                event.recipientId(),
                "forum_thread_tag",
                username + (event.carTagged() ? " tagged your car in a thread" : " tagged you in a thread"),
                null,
                payload);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(ForumReplyTaggedEvent event) {
        NotificationPreferencesDto prefs = profileService.getNotificationPreferencesOrDefault(event.recipientId());
        if (!prefs.tagsEnabled()) {
            return;
        }
        String username = resolveUsername(event.actorId());
        Map<String, Object> payload = basePayload(event.actorId(), username);
        payload.put("thread_id", event.threadId().toString());
        payload.put("reply_id", event.replyId().toString());
        payload.put("car_tagged", event.carTagged());
        notificationService.push(
                event.recipientId(),
                "forum_reply_tag",
                username + (event.carTagged() ? " tagged your car in a reply" : " tagged you in a reply"),
                null,
                payload);
    }

    /**
     * Resolves the actor's display username in a single batch call. Falls back to "Someone" if the
     * actor's profile can't be resolved (they just acted, so this is only a safety net).
     */
    private String resolveUsername(UUID actorId) {
        return profileService.findByIds(List.of(actorId)).stream()
                .findFirst()
                .map(ProfileSearchResultDto::username)
                .orElse("Someone");
    }

    /**
     * Payload seed carrying the actor identity. Keys are literal snake_case (the global wire strategy
     * renames POJO fields, not JSON-map keys, so we spell them out). Callers add target ids.
     */
    private static Map<String, Object> basePayload(UUID actorId, String username) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actor_id", actorId.toString());
        payload.put("actor_username", username);
        return payload;
    }
}
