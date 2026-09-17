package com.tweakdapp.backend.shared.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Only requests carrying Cloudflare's secret header get through — see {@link OriginSecretFilter}.
 */
class OriginSecretFilterTest {

    private static final String SECRET = "s3cr3t-value";

    private record Outcome(int status, boolean reachedChain) {
    }

    private static Outcome run(OriginSecretFilter filter, String path, String header)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (header != null) {
            request.addHeader(OriginSecretFilter.HEADER, header);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Outcome(response.getStatus(), chain.getRequest() != null);
    }

    @Test
    void letsThroughARequestWithTheSecret() throws Exception {
        Outcome outcome = run(new OriginSecretFilter(SECRET), "/api/v1/feed/global", SECRET);

        assertThat(outcome.reachedChain()).isTrue();
        assertThat(outcome.status()).isEqualTo(200);
    }

    @Test
    void rejectsARequestWithoutTheHeader() throws Exception {
        Outcome outcome = run(new OriginSecretFilter(SECRET), "/api/v1/feed/global", null);

        assertThat(outcome.reachedChain()).isFalse();
        assertThat(outcome.status()).isEqualTo(403);
    }

    @Test
    void rejectsARequestWithTheWrongSecret() throws Exception {
        Outcome outcome = run(new OriginSecretFilter(SECRET), "/api/v1/feed/global", "s3cr3t-valuf");

        assertThat(outcome.reachedChain()).isFalse();
        assertThat(outcome.status()).isEqualTo(403);
    }

    @Test
    void rejectsASecretThatOnlyStartsTheSame() throws Exception {
        Outcome outcome = run(new OriginSecretFilter(SECRET), "/api/v1/feed/global", SECRET + "x");

        assertThat(outcome.reachedChain()).isFalse();
    }

    @Test
    void guardsTheRoutesSpringSecurityLeavesOpen() throws Exception {
        OriginSecretFilter filter = new OriginSecretFilter(SECRET);

        assertThat(run(filter, "/public/v1/cars/abc123", null).status()).isEqualTo(403);
        assertThat(run(filter, "/ws", null).status()).isEqualTo(403);
    }

    @Test
    void letsEverythingThroughWhenNoSecretIsConfigured() throws Exception {
        for (String unset : new String[]{null, "", "   "}) {
            OriginSecretFilter filter = new OriginSecretFilter(unset);

            assertThat(filter.isEnabled()).isFalse();
            assertThat(run(filter, "/api/v1/feed/global", null).reachedChain()).isTrue();
        }
    }
}
