package com.tweakdapp.backend.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Rejects every request that did not come through Cloudflare.
 *
 * <p>Clients call {@code api.tweakdapp.com}, which Cloudflare proxies to Cloud Run through a
 * domain mapping. A Cloudflare request header Transform Rule adds {@value #HEADER} with a shared
 * secret on the way. The service's {@code *.run.app} URL stays reachable on the open internet
 * (restricting ingress to Cloudflare would need a paid load balancer), so this filter is what stops
 * a caller from skipping Cloudflare's DDoS protection by calling that URL directly.
 *
 * <p>Registered ahead of everything else by {@link OriginGuardConfig}, so a rejected request never
 * reaches JWT decoding, the ban check or the rate limiter — and it covers {@code /ws} and
 * {@code /public/**} too, which Spring Security lets through unauthenticated.
 *
 * <p>With no secret configured the filter lets everything through: local runs, tests, and the
 * rollout window before clients have moved to {@code api.tweakdapp.com}.
 */
class OriginSecretFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Origin-Secret";

    /** Null when the guard is disabled. */
    private final byte[] expected;

    OriginSecretFilter(String secret) {
        this.expected = secret == null || secret.isBlank() ? null : secret.getBytes(StandardCharsets.UTF_8);
    }

    boolean isEnabled() {
        return expected != null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isEnabled();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        // MessageDigest.isEqual takes the same time wherever the first mismatch is, so response
        // timing can't be used to guess the secret one character at a time.
        if (provided == null || !MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8))) {
            // setStatus rather than sendError: sendError would dispatch to /error and run the whole
            // filter chain again for a request we are refusing.
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }
}
