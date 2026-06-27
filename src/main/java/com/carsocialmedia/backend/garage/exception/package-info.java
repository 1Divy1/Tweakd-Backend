/**
 * Custom exceptions for the Garage module.
 *
 * <h2>Exception Mapping</h2>
 * <ul>
 *   <li><strong>NotFoundException (404)</strong>
 *     <ul>
 *       <li>{@link com.carsocialmedia.backend.garage.exception.GarageNotFoundException} - garage not found for user</li>
 *       <li>{@link com.carsocialmedia.backend.garage.exception.CarNotFoundException} - car not found by ID</li>
 *       <li>{@link com.carsocialmedia.backend.garage.exception.CarModificationNotFoundException} - modification not found or doesn't belong to car</li>
 *     </ul>
 *   </li>
 *   <li><strong>ForbiddenException (403)</strong>
 *     <ul>
 *       <li>{@link com.carsocialmedia.backend.garage.exception.NotCarOwnerException} - user is not the car owner</li>
 *     </ul>
 *   </li>
 *   <li><strong>BadRequestException (400)</strong>
 *     <ul>
 *       <li>{@link com.carsocialmedia.backend.garage.exception.InvalidReferenceException} - request references non-existent or inconsistent lookup table row</li>
 *     </ul>
 *   </li>
 * </ul>
 */
package com.carsocialmedia.backend.garage.exception;