package com.tweakdapp.backend.notification.internal.push;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sends notifications through Firebase Cloud Messaging.
 *
 * <p>Uses {@code sendEach(List<Message>)} rather than {@code sendEachForMulticast}: a multicast
 * delivers one identical message to every token, but the APNs badge is per-recipient, so each
 * message has to be built individually anyway. {@code sendEach} carries up to 500 distinct messages
 * per call. ({@code sendAll} / {@code sendMulticast} are not options — they used the legacy batch
 * endpoint Google switched off in June 2024.)
 *
 * <p>Failures are classified, not blanket-retried: only the error codes that mean "this token is
 * permanently dead" cause a prune. A transient FCM outage must never cost a user their device
 * registration.
 */
class FirebaseFcmSender implements FcmSender {

    private static final Logger log = LoggerFactory.getLogger(FirebaseFcmSender.class);

    /** FCM's hard cap on messages per {@code sendEach} call. */
    private static final int MAX_BATCH = 500;

    /** FCM caps the whole payload at 4 KB; keep the body well inside it and off a huge lock screen. */
    private static final int MAX_BODY_LENGTH = 200;

    /**
     * The token is dead and must be removed. Everything else ({@code UNAVAILABLE}, {@code INTERNAL},
     * {@code QUOTA_EXCEEDED}, {@code THIRD_PARTY_AUTH_ERROR}) is transient or an APNs-config problem
     * — the device is still fine, so the row stays.
     */
    private static final Set<MessagingErrorCode> DEAD_TOKEN_CODES = EnumSet.of(
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.INVALID_ARGUMENT,
            MessagingErrorCode.SENDER_ID_MISMATCH);

    private final FirebaseMessaging firebaseMessaging;
    private final FirebaseProperties properties;

    FirebaseFcmSender(FirebaseMessaging firebaseMessaging, FirebaseProperties properties) {
        this.firebaseMessaging = firebaseMessaging;
        this.properties = properties;
    }

    @Override
    public Set<String> send(List<PushMessage> messages) {
        Set<String> deadTokens = new LinkedHashSet<>();
        for (int start = 0; start < messages.size(); start += MAX_BATCH) {
            List<PushMessage> chunk = messages.subList(start, Math.min(start + MAX_BATCH, messages.size()));
            sendChunk(chunk, deadTokens);
        }
        return deadTokens;
    }

    private void sendChunk(List<PushMessage> chunk, Set<String> deadTokens) {
        List<Message> payloads = chunk.stream().map(this::toFcmMessage).toList();
        try {
            BatchResponse response = firebaseMessaging.sendEach(payloads, properties.isDryRun());
            // getResponses() is index-aligned with the input list, which is how a failure is traced
            // back to the token that caused it.
            List<SendResponse> results = response.getResponses();
            for (int i = 0; i < results.size(); i++) {
                SendResponse result = results.get(i);
                if (!result.isSuccessful()) {
                    classify(chunk.get(i).token(), result.getException(), deadTokens);
                }
            }
            if (response.getFailureCount() > 0) {
                log.warn("FCM batch: {} sent, {} failed", response.getSuccessCount(), response.getFailureCount());
            }
        } catch (FirebaseMessagingException e) {
            // Whole-batch failure (network, auth). Nothing is provably dead, so nothing is pruned.
            log.error("FCM batch of {} message(s) failed entirely: {}", chunk.size(), e.getMessagingErrorCode());
        }
    }

    private void classify(String token, FirebaseMessagingException failure, Set<String> deadTokens) {
        MessagingErrorCode code = failure == null ? null : failure.getMessagingErrorCode();
        if (code != null && DEAD_TOKEN_CODES.contains(code)) {
            deadTokens.add(token);
            log.debug("Pruning device token {} after {}", maskToken(token), code);
        } else {
            log.debug("Transient FCM failure for token {}: {}", maskToken(token), code);
        }
    }

    /**
     * Builds the hybrid notification+data message the Flutter client expects.
     *
     * <p>Every {@code data} value is a string — FCM rejects anything else, and the app's
     * {@code _parsePayload} assumes it. That matters here because {@code payload} legitimately holds
     * a Boolean ({@code car_tagged}).
     */
    private Message toFcmMessage(PushMessage message) {
        Message.Builder builder = Message.builder()
                .setToken(message.token())
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(truncate(message.body()))
                        .build())
                .putAllData(buildData(message))
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setChannelId(properties.getAndroidChannelId())
                                .build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder()
                                .setBadge((int) Math.min(message.unreadCount(), Integer.MAX_VALUE))
                                .setSound("default")
                                .build())
                        .build());
        return builder.build();
    }

    private static Map<String, String> buildData(PushMessage message) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", message.type());
        data.put("notification_id", message.notificationId().toString());
        data.put("unread_count", Long.toString(message.unreadCount()));

        if (message.payload() != null) {
            message.payload().forEach((key, value) -> {
                // Null values are dropped rather than stringified to the literal "null", which the
                // client would then have to special-case.
                if (key != null && value != null) {
                    data.put(key, String.valueOf(value));
                }
            });
        }
        return data;
    }

    private static String truncate(String body) {
        if (body == null) {
            return null;
        }
        return body.length() <= MAX_BODY_LENGTH ? body : body.substring(0, MAX_BODY_LENGTH - 1) + "…";
    }

    /** Tokens are device-addressable secrets; only ever log enough to correlate two log lines. */
    private static String maskToken(String token) {
        return token == null || token.length() < 8 ? "…" : token.substring(0, 8) + "…";
    }
}
