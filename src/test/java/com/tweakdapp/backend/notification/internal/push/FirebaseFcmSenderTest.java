package com.tweakdapp.backend.notification.internal.push;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FirebaseFcmSender}: the wire shape handed to FCM (all-string data, badge,
 * Android channel), the 500-per-call batching, and — most importantly — which failures are treated
 * as a dead token. Misclassifying a transient failure would silently unregister live devices.
 *
 * <p>{@link Message} exposes no getters, so wire assertions go through the SDK's own JSON
 * serializer, which is also what proves the payload FCM would actually receive.
 */
class FirebaseFcmSenderTest {

    private static final UUID NOTIFICATION_ID = UUID.fromString("00000000-0000-0000-0000-0000000000n1".replace('n', 'a'));
    private static final String TOKEN = "tok-alpha-0000000000000000000000000000000000";
    private static final String TOKEN_2 = "tok-beta-00000000000000000000000000000000000";

    private static final Gson GSON = new Gson();

    private FirebaseMessaging firebaseMessaging;
    private FirebaseFcmSender sender;

    @BeforeEach
    void setUp() {
        firebaseMessaging = mock(FirebaseMessaging.class);
        FirebaseProperties properties = new FirebaseProperties();
        properties.setAndroidChannelId("tweakd_default");
        sender = new FirebaseFcmSender(firebaseMessaging, properties);
    }

    private static PushMessage push(String token, Map<String, Object> payload) {
        return new PushMessage(token, NOTIFICATION_ID, "post_like", "alice liked your post",
                null, payload, 7L);
    }

    private static BatchResponse batchOf(SendResponse... responses) {
        BatchResponse batch = mock(BatchResponse.class);
        when(batch.getResponses()).thenReturn(List.of(responses));
        long failures = List.of(responses).stream().filter(r -> !r.isSuccessful()).count();
        when(batch.getFailureCount()).thenReturn((int) failures);
        when(batch.getSuccessCount()).thenReturn(responses.length - (int) failures);
        return batch;
    }

    /**
     * Stubs the next send. The {@link BatchResponse} is built <em>before</em> the {@code when(...)}
     * is opened — building it inside would start a nested stubbing and Mockito rejects that.
     */
    private void stubSend(SendResponse... responses) throws Exception {
        BatchResponse batch = batchOf(responses);
        when(firebaseMessaging.sendEach(anyList(), anyBoolean())).thenReturn(batch);
    }

    private static SendResponse ok() {
        SendResponse response = mock(SendResponse.class);
        when(response.isSuccessful()).thenReturn(true);
        return response;
    }

    private static SendResponse failedWith(MessagingErrorCode code) {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(code);
        SendResponse response = mock(SendResponse.class);
        when(response.isSuccessful()).thenReturn(false);
        when(response.getException()).thenReturn(exception);
        return response;
    }

    private List<Message> capturedMessages() throws Exception {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(firebaseMessaging).sendEach(captor.capture(), anyBoolean());
        return captor.getValue();
    }

    // ---- wire shape ---------------------------------------------------------

    @Test
    void everyDataValueIsSerialisedAsAStringIncludingBooleans() throws Exception {
        stubSend(ok());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("post_id", UUID.randomUUID());
        payload.put("car_tagged", Boolean.TRUE); // the Boolean that would otherwise break FCM
        payload.put("actor_username", "alice");

        sender.send(List.of(push(TOKEN, payload)));

        String json = GSON.toJson(capturedMessages().getFirst());
        assertThat(json).contains("\"car_tagged\":\"true\"");
        assertThat(json).contains("\"actor_username\":\"alice\"");
        assertThat(json).contains("\"type\":\"post_like\"");
        assertThat(json).contains("\"notification_id\":\"" + NOTIFICATION_ID + "\"");
        assertThat(json).contains("\"unread_count\":\"7\"");
    }

    @Test
    void nullPayloadValuesAreDroppedRatherThanStringifiedToTheWordNull() throws Exception {
        stubSend(ok());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("comment_id", null);

        sender.send(List.of(push(TOKEN, payload)));

        assertThat(GSON.toJson(capturedMessages().getFirst()))
                .doesNotContain("\"comment_id\"");
    }

    @Test
    void carriesTheAndroidChannelAndTheApnsBadgeAndSound() throws Exception {
        stubSend(ok());

        sender.send(List.of(push(TOKEN, Map.of())));

        String json = GSON.toJson(capturedMessages().getFirst());
        assertThat(json).contains("\"channelId\":\"tweakd_default\"");
        assertThat(json).contains("\"badge\":7");
        assertThat(json).contains("\"sound\":\"default\"");
    }

    @Test
    void longBodiesAreTruncatedToKeepThePayloadUnderTheFcmLimit() throws Exception {
        stubSend(ok());
        String longBody = "x".repeat(500);
        PushMessage message = new PushMessage(TOKEN, NOTIFICATION_ID, "post_comment", "title",
                longBody, Map.of(), 1L);

        sender.send(List.of(message));

        String json = GSON.toJson(capturedMessages().getFirst());
        assertThat(json).doesNotContain("x".repeat(300));
        assertThat(json).contains("…");
    }

    // ---- failure classification ---------------------------------------------

    @Test
    void unregisteredTokensAreReportedForPruning() throws Exception {
        stubSend(failedWith(MessagingErrorCode.UNREGISTERED));

        assertThat(sender.send(List.of(push(TOKEN, Map.of())))).containsExactly(TOKEN);
    }

    @Test
    void invalidArgumentAndSenderIdMismatchAreAlsoPruned() throws Exception {
        stubSend(
                failedWith(MessagingErrorCode.INVALID_ARGUMENT),
                failedWith(MessagingErrorCode.SENDER_ID_MISMATCH));

        assertThat(sender.send(List.of(push(TOKEN, Map.of()), push(TOKEN_2, Map.of()))))
                .containsExactlyInAnyOrder(TOKEN, TOKEN_2);
    }

    /** A transient outage must never cost a user their device registration. */
    @Test
    void transientFailuresDoNotPruneTheToken() throws Exception {
        stubSend(
                failedWith(MessagingErrorCode.UNAVAILABLE),
                failedWith(MessagingErrorCode.INTERNAL),
                failedWith(MessagingErrorCode.QUOTA_EXCEEDED));

        assertThat(sender.send(List.of(push(TOKEN, Map.of()), push(TOKEN_2, Map.of()),
                push("tok-gamma-0000000000000000000000000000000", Map.of())))).isEmpty();
    }

    @Test
    void aWholeBatchFailurePrunesNothing() throws Exception {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNAVAILABLE);
        when(firebaseMessaging.sendEach(anyList(), anyBoolean())).thenThrow(exception);

        assertThat(sender.send(List.of(push(TOKEN, Map.of())))).isEmpty();
    }

    @Test
    void deadAndLiveTokensAreDistinguishedByPositionInTheBatch() throws Exception {
        stubSend(ok(), failedWith(MessagingErrorCode.UNREGISTERED));

        Set<String> dead = sender.send(List.of(push(TOKEN, Map.of()), push(TOKEN_2, Map.of())));

        assertThat(dead).containsExactly(TOKEN_2);
    }

    // ---- batching -----------------------------------------------------------

    @Test
    void messagesAreSplitIntoCallsOfAtMost500() throws Exception {
        List<PushMessage> messages = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            messages.add(push("tok-" + i + "-".repeat(40), Map.of()));
        }
        when(firebaseMessaging.sendEach(anyList(), anyBoolean()))
                .thenAnswer(invocation -> {
                    List<?> batch = invocation.getArgument(0);
                    SendResponse[] responses = new SendResponse[batch.size()];
                    for (int i = 0; i < batch.size(); i++) {
                        responses[i] = ok();
                    }
                    return batchOf(responses);
                });

        sender.send(messages);

        verify(firebaseMessaging, times(2)).sendEach(anyList(), anyBoolean());
    }
}
