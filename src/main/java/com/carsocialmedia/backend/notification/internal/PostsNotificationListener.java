package com.carsocialmedia.backend.notification.internal;

import com.carsocialmedia.backend.notification.NotificationService;
import com.carsocialmedia.backend.posts.PostCommentTaggedEvent;
import com.carsocialmedia.backend.posts.PostCommentedEvent;
import com.carsocialmedia.backend.posts.PostLikedEvent;
import com.carsocialmedia.backend.posts.PostSharedEvent;
import com.carsocialmedia.backend.posts.PostTaggedEvent;
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
 * Turns {@code posts} engagement events into in-app notifications for the post's author. Each
 * handler runs asynchronously, in its own new transaction, after the producing transaction commits
 * ({@code @Async @Transactional(REQUIRES_NEW) @TransactionalEventListener} — the standard-Spring
 * equivalent of Modulith's {@code @ApplicationModuleListener}, which isn't on the classpath here),
 * so a rolled-back like/comment/share never notifies. The self-notify skip already happened at the
 * publish site; here we only gate on the recipient's preferences and resolve the actor's username.
 *
 * <p>Likes gate on {@code likes_enabled}, comments on {@code comments_enabled}, shares on
 * {@code shares_enabled}, tags on {@code tags_enabled}. Tag events go to the tagged user, not the
 * post's author.
 */
@Component
class PostsNotificationListener {

    private final ProfileService profileService;
    private final NotificationService notificationService;

    PostsNotificationListener(ProfileService profileService, NotificationService notificationService) {
        this.profileService = profileService;
        this.notificationService = notificationService;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(PostLikedEvent event) {
        NotificationPreferencesDto prefs = profileService.getNotificationPreferencesOrDefault(event.recipientId());
        if (!prefs.likesEnabled()) {
            return;
        }
        String username = resolveUsername(event.actorId());
        notificationService.push(
                event.recipientId(),
                "post_like",
                username + " liked your post",
                null,
                basePayload(event.actorId(), username, "post_id", event.postId()));
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(PostCommentedEvent event) {
        NotificationPreferencesDto prefs = profileService.getNotificationPreferencesOrDefault(event.recipientId());
        if (!prefs.commentsEnabled()) {
            return;
        }
        String username = resolveUsername(event.actorId());
        Map<String, Object> payload = basePayload(event.actorId(), username, "post_id", event.postId());
        payload.put("comment_id", event.commentId().toString());
        notificationService.push(
                event.recipientId(),
                "post_comment",
                username + " commented on your post",
                event.excerpt(),
                payload);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(PostSharedEvent event) {
        NotificationPreferencesDto prefs = profileService.getNotificationPreferencesOrDefault(event.recipientId());
        if (!prefs.sharesEnabled()) {
            return;
        }
        String username = resolveUsername(event.actorId());
        notificationService.push(
                event.recipientId(),
                "post_share",
                username + " shared your post",
                null,
                basePayload(event.actorId(), username, "post_id", event.postId()));
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(PostTaggedEvent event) {
        NotificationPreferencesDto prefs = profileService.getNotificationPreferencesOrDefault(event.recipientId());
        if (!prefs.tagsEnabled()) {
            return;
        }
        String username = resolveUsername(event.actorId());
        Map<String, Object> payload = basePayload(event.actorId(), username, "post_id", event.postId());
        payload.put("car_tagged", event.carTagged());
        notificationService.push(
                event.recipientId(),
                "post_tag",
                username + (event.carTagged() ? " tagged your car in a post" : " tagged you in a post"),
                null,
                payload);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(PostCommentTaggedEvent event) {
        NotificationPreferencesDto prefs = profileService.getNotificationPreferencesOrDefault(event.recipientId());
        if (!prefs.tagsEnabled()) {
            return;
        }
        String username = resolveUsername(event.actorId());
        Map<String, Object> payload = basePayload(event.actorId(), username, "post_id", event.postId());
        payload.put("comment_id", event.commentId().toString());
        payload.put("car_tagged", event.carTagged());
        notificationService.push(
                event.recipientId(),
                "post_comment_tag",
                username + (event.carTagged() ? " tagged your car in a comment" : " tagged you in a comment"),
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
     * Payload seed carrying the actor identity plus one target id. Keys are literal snake_case (the
     * global wire strategy renames POJO fields, not JSON-map keys, so we spell them out).
     */
    private static Map<String, Object> basePayload(UUID actorId, String username, String targetKey, UUID targetId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actor_id", actorId.toString());
        payload.put("actor_username", username);
        payload.put(targetKey, targetId.toString());
        return payload;
    }
}
