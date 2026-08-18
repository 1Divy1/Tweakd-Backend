/**
 * The Feedback Feed module owns the app's <strong>public community feedback board</strong>: short
 * messages users publish under one of three categories, which everyone else can up- or downvote,
 * and which the staff move along a small roadmap and optionally reply to.
 *
 * <h2>Relationship to the {@code feedback} module</h2>
 * This is <strong>not</strong> the older {@code feedback} module. That one backs the dashboard's
 * feedback board ({@code feedback}, {@code feedback_votes}, {@code feedback_comments},
 * {@code feedback_subscriptions}) with a nine-status lifecycle, comments and subscriptions. This
 * module owns the {@code feedback_feed_*} tables and the in-app community feed the mobile client
 * shows. The two coexist deliberately — the old one is still wired into the app and will be retired
 * separately.
 *
 * <h2>Lifecycle</h2>
 * A message starts {@code sent}. Staff move it to {@code under_development} (the client renders an
 * "in progress" pill) and finally {@code completed}. Completed messages leave the main feed and
 * appear in their own "Completed requests" section, ordered by ship date; the database blocks any
 * vote write against them.
 *
 * <h2>Voting</h2>
 * One row per (user, message) in {@code feedback_feed_votes}, {@code vote_type} of {@code +1} or
 * {@code -1}. Casting the same direction twice removes the vote, the opposite direction switches
 * it, and authors may vote on their own messages. {@code up_votes} / {@code down_votes} /
 * {@code net_votes} on the message are <strong>trigger-maintained</strong> and never written here.
 *
 * <h2>Deletion is asymmetric on purpose</h2>
 * An author deleting their own message <strong>hard deletes</strong> it (its votes cascade), and
 * only while it is still {@code sent} — after staff put it on the roadmap it belongs to the
 * community and the author is refused. {@code is_deleted} exists for <em>staff</em> removing spam
 * or abuse, which keeps the row for audit while hiding it from every read, at any status.
 *
 * <h2>Main API</h2>
 * Public interface: {@link com.carsocialmedia.backend.feedbackfeed.FeedbackFeedService}
 * <ul>
 *   <li><strong>Feed:</strong> getFeed (newest / popular / oldest), getCompleted, getMessage</li>
 *   <li><strong>Authoring:</strong> submit, delete own</li>
 *   <li><strong>Voting:</strong> vote (toggle / switch), removeVote</li>
 *   <li><strong>Reference data:</strong> listTypes, listStatuses</li>
 *   <li><strong>Admin:</strong> listAll, listTopVoted, updateStatus, respond, removeAsStaff —
 *       called by the {@code admin} module, which owns the dashboard endpoints and the
 *       {@code MANAGE_ROADMAP} capability check</li>
 * </ul>
 *
 * <h2>Cross-module dependencies</h2>
 * {@code profile} (author cards) and {@code shared} (exception hierarchy). Notably <strong>not</strong>
 * {@code notification}: the author's "your feedback status changed" notification is written by a
 * Supabase trigger on {@code feedback_feed_messages}, so no application fan-out exists or is needed.
 */
@ApplicationModule(
        displayName = "Feedback Feed"
)
package com.carsocialmedia.backend.feedbackfeed;

import org.springframework.modulith.ApplicationModule;
