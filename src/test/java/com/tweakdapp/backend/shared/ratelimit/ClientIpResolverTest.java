package com.tweakdapp.backend.shared.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How an unauthenticated caller is identified. The header can be forged, so which entry is read
 * matters — see {@link ClientIpResolver} for why the last one is the default.
 */
class ClientIpResolverTest {

    private static MockHttpServletRequest request(String forwardedFor, String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    @Test
    void takesTheLastForwardedEntryByDefault() {
        ClientIpResolver resolver = new ClientIpResolver(RateLimitProperties.ClientIpStrategy.XFF_LAST);

        // A client that sends "1.1.1.1" to look like someone else is still counted as the address
        // the proxy appended.
        assertThat(resolver.resolve(request("1.1.1.1, 203.0.113.7", "169.254.1.1")))
                .isEqualTo("203.0.113.7");
    }

    @Test
    void takesTheFirstForwardedEntryWhenConfiguredTo() {
        ClientIpResolver resolver = new ClientIpResolver(RateLimitProperties.ClientIpStrategy.XFF_FIRST);

        assertThat(resolver.resolve(request("198.51.100.5, 203.0.113.7", "169.254.1.1")))
                .isEqualTo("198.51.100.5");
    }

    @Test
    void ignoresTheHeaderWhenConfiguredToUseTheSocketAddress() {
        ClientIpResolver resolver = new ClientIpResolver(RateLimitProperties.ClientIpStrategy.REMOTE_ADDR);

        assertThat(resolver.resolve(request("198.51.100.5", "169.254.1.1"))).isEqualTo("169.254.1.1");
    }

    @Test
    void fallsBackToTheSocketAddressWhenTheHeaderIsAbsentOrEmpty() {
        ClientIpResolver resolver = new ClientIpResolver(RateLimitProperties.ClientIpStrategy.XFF_LAST);

        assertThat(resolver.resolve(request(null, "169.254.1.1"))).isEqualTo("169.254.1.1");
        assertThat(resolver.resolve(request("   ", "169.254.1.1"))).isEqualTo("169.254.1.1");
        assertThat(resolver.resolve(request("1.1.1.1,   ", "169.254.1.1"))).isEqualTo("169.254.1.1");
    }

    @Test
    void neverReturnsNullSoEveryRequestGetsABucket() {
        ClientIpResolver resolver = new ClientIpResolver(RateLimitProperties.ClientIpStrategy.XFF_LAST);

        assertThat(resolver.resolve(request(null, null))).isEqualTo("unknown");
    }
}
