/**
 * Data Transfer Objects for the Feedback Feed module.
 *
 * <ul>
 *   <li>{@link com.tweakdapp.backend.feedbackfeed.dto.FeedbackMessageDto} — one feed card,
 *       carrying the author's original message <em>and</em> the staff response side by side; the
 *       client renders the response beneath the message rather than in place of it</li>
 *   <li>{@link com.tweakdapp.backend.feedbackfeed.dto.FeedbackAuthorDto} — the author card</li>
 *   <li>{@link com.tweakdapp.backend.feedbackfeed.dto.FeedbackFeedPageDto} — one keyset page</li>
 *   <li>{@link com.tweakdapp.backend.feedbackfeed.dto.FeedbackCategoryDto} /
 *       {@link com.tweakdapp.backend.feedbackfeed.dto.FeedbackFeedStatusDto} — reference data
 *       backing the category picker and the status chips</li>
 * </ul>
 *
 * <p>Exposed as a named interface so the {@code admin} dashboard can consume these DTOs without
 * crossing into {@code feedbackfeed.internal}.
 */
@NamedInterface("dto")
package com.tweakdapp.backend.feedbackfeed.dto;

import org.springframework.modulith.NamedInterface;
