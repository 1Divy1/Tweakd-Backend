/**
 * Custom exceptions for the Business module.
 *
 * <ul>
 *   <li><strong>NotFoundException (404)</strong> —
 *       {@link com.tweakdapp.backend.business.exception.BusinessNotFoundException}: unknown
 *       business, or one that is not active and verified</li>
 *   <li><strong>BadRequestException (400)</strong> —
 *       {@link com.tweakdapp.backend.business.exception.InvalidSearchAreaException}: map search
 *       coordinates, radius or limit out of bounds</li>
 * </ul>
 */
package com.tweakdapp.backend.business.exception;
