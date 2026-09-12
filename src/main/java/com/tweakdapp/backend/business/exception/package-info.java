/**
 * Custom exceptions for the Business module.
 *
 * <ul>
 *   <li><strong>NotFoundException (404)</strong> —
 *       {@link com.tweakdapp.backend.business.exception.BusinessNotFoundException}: unknown
 *       business, or one that is not active and verified</li>
 *   <li><strong>BadRequestException (400)</strong> —
 *       {@link com.tweakdapp.backend.business.exception.InvalidSearchAreaException}: map search
 *       coordinates, radius or limit out of bounds;
 *       {@link com.tweakdapp.backend.business.exception.InvalidBusinessStatusException}: unknown
 *       status or a nonsensical review transition;
 *       {@link com.tweakdapp.backend.business.exception.InvalidBusinessCursorException}: an
 *       unparseable review-queue cursor</li>
 * </ul>
 */
package com.tweakdapp.backend.business.exception;
