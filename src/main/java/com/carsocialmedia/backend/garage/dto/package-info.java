/**
 * Data Transfer Objects (DTOs) for the Garage module.
 *
 * <h2>Response DTOs</h2>
 * <ul>
 *   <li>{@link com.carsocialmedia.backend.garage.dto.GarageDto} - complete garage with car list</li>
 *   <li>{@link com.carsocialmedia.backend.garage.dto.CarSummaryDto} - compact car projection for list view</li>
 *   <li>{@link com.carsocialmedia.backend.garage.dto.CarDto} - full car detail with all specs and modifications</li>
 *   <li>{@link com.carsocialmedia.backend.garage.dto.CarModificationDto} - modification with price visibility control</li>
 * </ul>
 *
 * <h2>Request DTOs</h2>
 * <ul>
 *   <li>{@link com.carsocialmedia.backend.garage.dto.request.CarRequest} - payload for POST/PUT car (all fields required)</li>
 *   <li>{@link com.carsocialmedia.backend.garage.dto.request.CarModificationRequest} - payload for POST/PUT modification (includes price visibility validation)</li>
 * </ul>
 *
 * <h2>Reference Data DTOs</h2>
 * <ul>
 *   <li>{@link com.carsocialmedia.backend.garage.dto.CarBrandDto} - car brand (name + ID)</li>
 *   <li>{@link com.carsocialmedia.backend.garage.dto.CarModelDto} - car model (name + brand ID)</li>
 *   <li>{@link com.carsocialmedia.backend.garage.dto.CarDrivetrainDto} - drivetrain type</li>
 *   <li>{@link com.carsocialmedia.backend.garage.dto.CarColorDto} - paint color with hex code</li>
 *   <li>{@link com.carsocialmedia.backend.garage.dto.CarDistanceUnitDto} - distance unit (km, miles, etc.)</li>
 *   <li>{@link com.carsocialmedia.backend.garage.dto.CarStatusOptionDto} - car status/role</li>
 *   <li>{@link com.carsocialmedia.backend.garage.dto.CarModCategoryDto} - modification category</li>
 * </ul>
 *
 * <h2>Design Notes</h2>
 * <ul>
 *   <li><strong>Denormalization:</strong> CarDto includes both reference IDs and display labels (e.g., brandId + brandName) to avoid client round-trips for lookups</li>
 *   <li><strong>Request PUT Semantics:</strong> CarRequest and CarModificationRequest require all fields, even on updates (no partial updates)</li>
 *   <li><strong>Price Privacy:</strong> CarModificationDto.price is null when the viewer is not the owner and the price is marked private</li>
 *   <li><strong>Validation:</strong> Request DTOs use Jakarta validation annotations (NotNull, NotBlank, Size, etc.). CarModificationRequest additionally validates price/visibility consistency</li>
 * </ul>
 *
 * <p>Exposed as a named interface so other modules (e.g. {@code posts}) can consume these
 * DTOs without crossing into {@code garage.internal}.
 */
@NamedInterface("dto")
package com.carsocialmedia.backend.garage.dto;

import org.springframework.modulith.NamedInterface;