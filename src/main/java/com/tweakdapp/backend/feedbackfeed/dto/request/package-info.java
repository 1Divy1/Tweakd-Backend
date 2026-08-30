/**
 * Request payloads for the Feedback Feed module.
 *
 * <ul>
 *   <li>{@link com.tweakdapp.backend.feedbackfeed.dto.request.SubmitFeedbackMessageRequest} —
 *       publishing a message (category + text)</li>
 *   <li>{@link com.tweakdapp.backend.feedbackfeed.dto.request.FeedbackVoteRequest} — casting a
 *       vote</li>
 *   <li>{@link com.tweakdapp.backend.feedbackfeed.dto.request.FeedbackStaffResponseRequest} /
 *       {@link com.tweakdapp.backend.feedbackfeed.dto.request.UpdateFeedbackFeedStatusRequest}
 *       — the dashboard's two write operations</li>
 * </ul>
 *
 * <p>Exposed as a named interface so the {@code admin} module's controllers can bind the two staff
 * payloads without crossing into {@code feedbackfeed.internal}.
 */
@NamedInterface("dto-request")
package com.tweakdapp.backend.feedbackfeed.dto.request;

import org.springframework.modulith.NamedInterface;
