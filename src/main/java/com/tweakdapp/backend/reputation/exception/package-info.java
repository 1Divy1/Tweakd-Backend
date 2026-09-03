/**
 * Custom exceptions for the Reputation module.
 *
 * <ul>
 *   <li><strong>NotFoundException (404)</strong> —
 *       {@link com.tweakdapp.backend.reputation.exception.ReputationReasonNotFoundException}:
 *       an award quoted a reason code that is not in the catalogue, or one that has been retired</li>
 *   <li><strong>ConflictException (409)</strong> —
 *       {@link com.tweakdapp.backend.reputation.exception.DuplicateReputationAwardException}:
 *       a second award of a one-time achievement to the same user</li>
 *   <li><strong>BadRequestException (400)</strong> —
 *       {@link com.tweakdapp.backend.reputation.exception.InvalidReputationAwardException}:
 *       an award that would move the score by nothing;
 *       {@link com.tweakdapp.backend.reputation.exception.InvalidReputationCursorException}:
 *       an undecodable pagination token</li>
 * </ul>
 */
package com.tweakdapp.backend.reputation.exception;
