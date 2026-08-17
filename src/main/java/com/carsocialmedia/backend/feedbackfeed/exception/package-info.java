/**
 * Custom exceptions for the Feedback Feed module.
 *
 * <ul>
 *   <li><strong>NotFoundException (404)</strong> —
 *       {@link com.carsocialmedia.backend.feedbackfeed.exception.FeedbackMessageNotFoundException}:
 *       unknown message, or one a staff member has removed</li>
 *   <li><strong>ForbiddenException (403)</strong> —
 *       {@link com.carsocialmedia.backend.feedbackfeed.exception.NotFeedbackAuthorException}:
 *       deleting somebody else's message</li>
 *   <li><strong>ConflictException (409)</strong> —
 *       {@link com.carsocialmedia.backend.feedbackfeed.exception.FeedbackVotingClosedException}:
 *       voting on a completed message, which the database refuses outright</li>
 *   <li><strong>BadRequestException (400)</strong> —
 *       {@link com.carsocialmedia.backend.feedbackfeed.exception.InvalidFeedbackFeedRequestException}:
 *       an unknown category, status, sort or vote value;
 *       {@link com.carsocialmedia.backend.feedbackfeed.exception.InvalidFeedbackFeedCursorException}:
 *       an undecodable pagination token, or one replayed against a different sort</li>
 * </ul>
 */
package com.carsocialmedia.backend.feedbackfeed.exception;
