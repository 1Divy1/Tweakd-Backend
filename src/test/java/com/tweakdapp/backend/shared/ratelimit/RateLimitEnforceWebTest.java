package com.tweakdapp.backend.shared.ratelimit;

import com.tweakdapp.backend.testsupport.AppWebMvcTest;
import com.tweakdapp.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The limiter in {@code ENFORCE} mode, over MockMvc with a throwaway probe controller.
 *
 * <p>Limits are tiny here so the buckets drain in a few requests. Each test uses its own user id
 * or IP, because buckets live for the lifetime of the context and would otherwise leak between
 * tests.
 */
@AppWebMvcTest(RateLimitEnforceWebTest.ProbeController.class)
@Import(RateLimitEnforceWebTest.ProbeController.class)
@TestPropertySource(properties = {
        "rate-limit.mode=ENFORCE",
        "rate-limit.general.capacity=3",
        "rate-limit.general.refill-tokens=3",
        "rate-limit.general.refill-period=1m",
        "rate-limit.public-backstop.capacity=2",
        "rate-limit.public-backstop.refill-tokens=2",
        "rate-limit.public-backstop.refill-period=1m",
        "rate-limit.limits.reports.capacity=1",
        "rate-limit.limits.reports.refill-tokens=1",
        "rate-limit.limits.reports.refill-period=1m",
})
class RateLimitEnforceWebTest {

    @Autowired
    private MockMvc mockMvc;

    @RestController
    static class ProbeController {

        @GetMapping("/api/v1/probe")
        String probe() {
            return "ok";
        }

        /** Stands in for any endpoint carrying a named limit. */
        @GetMapping("/api/v1/probe/limited")
        @RateLimited(RateLimits.REPORTS)
        String limited() {
            return "ok";
        }

        @GetMapping("/public/v1/probe")
        String publicProbe() {
            return "ok";
        }
    }

    @Test
    void requestsPassUntilTheGeneralBucketIsEmpty() throws Exception {
        UUID user = UUID.randomUUID();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/probe").with(TestJwts.user(user)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/probe").with(TestJwts.user(user)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void rejectionCarriesRetryAfterAndTheStandardErrorBody() throws Exception {
        UUID user = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/probe").with(TestJwts.user(user)));
        }

        String retryAfter = mockMvc.perform(get("/api/v1/probe").with(TestJwts.user(user)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                // The app branches on this code, not on the message.
                .andExpect(jsonPath("$.error").value("rate_limited"))
                .andExpect(jsonPath("$.details.limit").value(RateLimits.GENERAL))
                .andExpect(jsonPath("$.details.retry_after_seconds").exists())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andReturn().getResponse().getHeader(HttpHeaders.RETRY_AFTER);

        // Never 0 — that would invite an immediate retry.
        assertThat(Long.parseLong(retryAfter)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void namedEndpointLimitRejectsWhileTheGeneralBucketStillHasTokens() throws Exception {
        UUID user = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/probe/limited").with(TestJwts.user(user)))
                .andExpect(status().isOk());

        // 'reports' holds one token; the general bucket (3) is not the one rejecting here.
        mockMvc.perform(get("/api/v1/probe/limited").with(TestJwts.user(user)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.details.limit").value(RateLimits.REPORTS));

        // Proof it was the named limit: an unannotated route for the same user still works.
        mockMvc.perform(get("/api/v1/probe").with(TestJwts.user(user)))
                .andExpect(status().isOk());
    }

    @Test
    void oneUserHittingALimitDoesNotAffectAnother() throws Exception {
        UUID spammer = UUID.randomUUID();
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(get("/api/v1/probe").with(TestJwts.user(spammer)));
        }
        mockMvc.perform(get("/api/v1/probe").with(TestJwts.user(spammer)))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/v1/probe").with(TestJwts.user(UUID.randomUUID())))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedPublicRequestsAreCountedByIp() throws Exception {
        String ip = "203.0.113.10";

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/public/v1/probe").header("X-Forwarded-For", ip))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/public/v1/probe").header("X-Forwarded-For", ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.details.limit").value(RateLimits.PUBLIC_BACKSTOP));

        // A different visitor is unaffected.
        mockMvc.perform(get("/public/v1/probe").header("X-Forwarded-For", "203.0.113.11"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousAndAuthenticatedCallersAreCountedSeparately() throws Exception {
        String ip = "203.0.113.20";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/public/v1/probe").header("X-Forwarded-For", ip));
        }

        // The IP bucket is empty; a signed-in caller from the same address is not affected,
        // because authenticated traffic is keyed by JWT subject.
        mockMvc.perform(get("/api/v1/probe")
                        .header("X-Forwarded-For", ip)
                        .with(TestJwts.user(UUID.randomUUID())))
                .andExpect(status().isOk());
    }
}
