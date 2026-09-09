package com.tweakdapp.backend.shared.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Publishes to Supabase Realtime <em>Broadcast</em> over its REST endpoint, with the service key —
 * the backend's way of pushing a live update to app clients subscribed to a private topic.
 *
 * <p>This is the second realtime channel next to the STOMP endpoint in {@link WebSocketConfig}.
 * The two are not interchangeable: STOMP delivers to a user's private queue on this JVM, which is
 * right for DMs; Broadcast fans one message out to every client on a topic, which is right for a
 * live scoreboard everyone at a meet is watching. The app already speaks Broadcast (DMs ride it),
 * so no new client dependency is needed.
 *
 * <p>Read access to a topic is governed by policies on {@code realtime.messages}; clients never
 * publish (there is no insert policy), which is why this client alone holds the key.
 *
 * <p><strong>Best effort.</strong> A failed publish is logged and dropped — the app polls as a
 * fallback, and a missed board update costs a second or two of staleness, never correctness.
 * The request has a two-second budget so a slow Realtime never holds a request thread.
 */
@Component
public class SupabaseBroadcastClient {

    private static final Logger log = LoggerFactory.getLogger(SupabaseBroadcastClient.class);

    private final RestClient restClient;

    public SupabaseBroadcastClient(@Value("${supabase.url}") String supabaseUrl,
                                   @Value("${supabase.secret-key}") String secretKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder()
                .baseUrl(supabaseUrl + "/realtime/v1/api")
                .requestFactory(factory)
                .defaultHeader("apikey", secretKey)
                .defaultHeader("Authorization", "Bearer " + secretKey)
                .build();
    }

    /**
     * Sends one message to one private topic.
     *
     * @param topic   e.g. {@code event:<uuid>:contests}
     * @param event   the event name clients subscribe to ({@code board}, {@code status})
     * @param payload serialised as the message body; keep it free of anything personal, since
     *                everyone on the topic receives it
     * @return whether Realtime accepted it
     */
    public boolean broadcast(String topic, String event, Object payload) {
        try {
            restClient.post()
                    .uri("/broadcast")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("messages", List.of(Map.of(
                            "topic", topic,
                            "event", event,
                            "payload", payload,
                            "private", true))))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("Realtime broadcast to {} ({}) failed: {}", topic, event, e.getMessage());
            return false;
        }
    }
}
