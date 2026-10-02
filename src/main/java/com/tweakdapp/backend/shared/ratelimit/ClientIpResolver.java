package com.tweakdapp.backend.shared.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Identifies an unauthenticated caller by IP address.
 *
 * <p>On Cloud Run the socket address belongs to Google's frontend, never to the caller, so the
 * address has to come from {@code X-Forwarded-For}. That header is a comma-separated chain and a
 * client can send one of its own, which a proxy then appends to — so <strong>the first entry is
 * attacker-controlled</strong> and the trustworthy entry is the one the closest proxy added, at the
 * end. Hence {@link RateLimitProperties.ClientIpStrategy#XFF_LAST} by default.
 *
 * <p>The exact shape of the header on a direct {@code run.app} request is still unverified
 * (it depends on what Google's front end appends); switching strategies is a config
 * change, no code edit. Getting it wrong is not severe here: the only unauthenticated route is
 * {@code /public/**}, whose real traffic arrives through the web.tweakdapp.com Worker and so shares
 * one IP anyway, which is why that limit is deliberately generous. Authenticated traffic is keyed
 * by JWT subject and never touches this class.
 */
public class ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String UNKNOWN = "unknown";

    private final RateLimitProperties.ClientIpStrategy strategy;

    ClientIpResolver(RateLimitProperties.ClientIpStrategy strategy) {
        this.strategy = strategy;
    }

    /** Never null, so a request always has a bucket — falls back to a constant if all else fails. */
    String resolve(HttpServletRequest request) {
        if (strategy != RateLimitProperties.ClientIpStrategy.REMOTE_ADDR) {
            String forwarded = fromForwardedHeader(request.getHeader(X_FORWARDED_FOR));
            if (forwarded != null) {
                return forwarded;
            }
        }
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? UNKNOWN : remoteAddress;
    }

    private String fromForwardedHeader(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String[] entries = header.split(",");
        int index = strategy == RateLimitProperties.ClientIpStrategy.XFF_FIRST ? 0 : entries.length - 1;
        String candidate = entries[index].trim();
        return candidate.isEmpty() ? null : candidate;
    }
}
