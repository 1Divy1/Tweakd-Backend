package com.tweakdapp.backend.shared.ratelimit;

import com.tweakdapp.backend.shared.exception.ErrorResponse;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Counts requests per caller and refuses the ones over a limit with 429.
 *
 * <p>Runs as an MVC interceptor after the security filter chain (so the JWT principal is
 * available) and after {@code BannedUserInterceptor}, so a banned account is rejected on its own
 * terms rather than being told to slow down.
 *
 * <p>Every request consumes one token from the caller's general bucket; a handler annotated with
 * {@link RateLimited} consumes from that named bucket as well. The general token is spent even when
 * the named limit is what rejects the request — the two buckets are independent, and reconciling
 * them would mean a probe-then-consume dance for no practical gain.
 *
 * <p>In {@link RateLimitProperties.Mode#LOG_ONLY} nothing is blocked: the decision is logged and
 * the request proceeds. That is the mode to run first, then tune the numbers from the logs, then
 * switch to {@link RateLimitProperties.Mode#ENFORCE}.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimitProperties properties;
    private final RateLimiterRegistry registry;
    private final ClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper;

    RateLimitInterceptor(RateLimitProperties properties,
                         RateLimiterRegistry registry,
                         ClientIpResolver clientIpResolver,
                         ObjectMapper objectMapper) {
        this.properties = properties;
        this.registry = registry;
        this.clientIpResolver = clientIpResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (properties.getMode() == RateLimitProperties.Mode.OFF || CorsUtils.isPreFlightRequest(request)) {
            return true;
        }

        Caller caller = resolveCaller(request);

        Decision general = check(caller, caller.generalLimitName(), caller.generalLimit(properties));
        if (!general.allowed()) {
            return reject(request, response, caller, general);
        }

        String endpointLimitName = endpointLimitName(handler);
        if (endpointLimitName != null) {
            Decision endpoint = check(caller, endpointLimitName, properties.limit(endpointLimitName));
            if (!endpoint.allowed()) {
                return reject(request, response, caller, endpoint);
            }
        }

        return true;
    }

    /**
     * Who to count. The JWT subject is unspoofable and follows a user across devices and networks;
     * an unauthenticated caller can only be counted by IP.
     */
    private Caller resolveCaller(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return new Caller("user:" + jwt.getSubject(), true);
        }
        return new Caller("ip:" + clientIpResolver.resolve(request), false);
    }

    /** The limit name on the handler method, else on its controller class, else none. */
    private String endpointLimitName(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return null;
        }
        RateLimited annotation = handlerMethod.getMethodAnnotation(RateLimited.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RateLimited.class);
        }
        return annotation == null ? null : annotation.value();
    }

    private Decision check(Caller caller, String limitName, RateLimitProperties.Limit limit) {
        if (limit == null) {
            // An annotation naming a limit that is not configured. Fail open rather than guess a
            // number, and make the misconfiguration loud — RateLimitConfigIT exists to catch it
            // before it ever ships.
            log.error("Rate limit '{}' is not configured; requests are not being limited by it", limitName);
            return Decision.allowed(limitName);
        }

        ConsumptionProbe probe = registry.bucket(caller.key(limitName), limit).tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return Decision.allowed(limitName);
        }
        return Decision.denied(limitName, ceilSeconds(probe.getNanosToWaitForRefill()));
    }

    /**
     * Blocks in {@code ENFORCE}, lets the request through in {@code LOG_ONLY}. Either way the
     * decision is logged at WARN — those log lines are the data the limits get tuned from.
     */
    private boolean reject(HttpServletRequest request, HttpServletResponse response,
                           Caller caller, Decision decision) throws IOException {
        boolean enforcing = properties.getMode() == RateLimitProperties.Mode.ENFORCE;
        log.warn("Rate limit '{}' exceeded by {} on {} {} — retry after {}s ({})",
                decision.limitName(), caller.identity(), request.getMethod(), request.getRequestURI(),
                decision.retryAfterSeconds(), enforcing ? "rejected" : "allowed, LOG_ONLY");

        if (!enforcing) {
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        // Same body shape as ApiException responses, so the app parses one error format. The
        // `error` code is what it should branch on; `details` carries the wait in a usable form.
        ErrorResponse body = new ErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too many requests. Try again in " + decision.retryAfterSeconds() + "s.",
                null,
                Instant.now(),
                "rate_limited",
                Map.of("limit", decision.limitName(), "retry_after_seconds", decision.retryAfterSeconds()));
        response.getWriter().write(objectMapper.writeValueAsString(body));
        return false;
    }

    /** Round up: a {@code Retry-After} of 0 would invite an immediate retry. */
    private static long ceilSeconds(long nanos) {
        return Math.max(1, TimeUnit.NANOSECONDS.toSeconds(nanos + 999_999_999L));
    }

    /** The counted identity and which broad limit applies to it. */
    private record Caller(String identity, boolean authenticated) {

        String key(String limitName) {
            return identity + "|" + limitName;
        }

        String generalLimitName() {
            return authenticated ? RateLimits.GENERAL : RateLimits.PUBLIC_BACKSTOP;
        }

        RateLimitProperties.Limit generalLimit(RateLimitProperties properties) {
            return authenticated ? properties.getGeneral() : properties.getPublicBackstop();
        }
    }

    private record Decision(String limitName, boolean allowed, long retryAfterSeconds) {

        static Decision allowed(String limitName) {
            return new Decision(limitName, true, 0);
        }

        static Decision denied(String limitName, long retryAfterSeconds) {
            return new Decision(limitName, false, retryAfterSeconds);
        }
    }
}
