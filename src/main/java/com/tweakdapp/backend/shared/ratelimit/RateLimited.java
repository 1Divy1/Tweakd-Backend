package com.tweakdapp.backend.shared.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method (or a whole controller) as subject to a named rate limit, on top of
 * the general per-caller limit every request already gets.
 *
 * <pre>{@code
 * @PostMapping("/{postId}/comments")
 * @RateLimited(RateLimits.COMMENTS)
 * public CommentDto comment(...) { ... }
 * }</pre>
 *
 * <p>The annotation carries only the limit's <em>name</em>; the numbers live in
 * {@link RateLimitProperties}, so tuning a limit is a config change rather than an edit spread
 * across controllers. A method-level annotation wins over a class-level one.
 *
 * <p>Use a constant from {@link RateLimits}, never a literal.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface RateLimited {

    /** The configured limit's name — a {@link RateLimits} constant. */
    String value();
}
