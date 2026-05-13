/**
 * The Garage module manages user car collections and modifications.
 *
 * <h2>Overview</h2>
 * Each user has one garage containing cars they own. Cars can have detailed specifications
 * (year, horsepower, weight, etc.) and a list of modifications (upgrades, customizations).
 * Modifications track before/after images, installation date, and optionally the cost.
 *
 * <h2>Privacy Model</h2>
 * Garage visibility follows the profile's privacy settings:
 * <ul>
 *   <li>The owner can always view their own garage</li>
 *   <li>Other users can view a garage only if:
 *     <ul>
 *       <li>The garage owner has a public profile, OR</li>
 *       <li>The viewer is an accepted follower of the garage owner</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>Price Privacy</h2>
 * Modification prices are controlled by the car owner's {@code isPricePublic} flag:
 * <ul>
 *   <li>The owner always sees the price</li>
 *   <li>Other users see the price only if the owner marked it public; otherwise null is returned</li>
 * </ul>
 *
 * <h2>Main API</h2>
 * Public interface: {@link com.carsocialmedia.backend.garage.GarageService}
 * <ul>
 *   <li><strong>Garage Views:</strong> getMyGarage, getGarageByUsername</li>
 *   <li><strong>Cars:</strong> addCar, updateCar, deleteCar, getCar</li>
 *   <li><strong>Modifications:</strong> addModification, updateModification, deleteModification</li>
 *   <li><strong>Reference Data:</strong> listBrands, listModelsByBrand, listDrivetrains, listColors, listDistanceUnits, listStatusOptions, listModCategories</li>
 * </ul>
 *
 * <h2>Database Constraints</h2>
 * <ul>
 *   <li>Brand/model consistency: model.brand_id must equal the selected brand</li>
 *   <li>Price visibility: if isPricePublic=true, price must be set</li>
 *   <li>Cascading deletes: deleting a car cascades to delete its modifications</li>
 *   <li>createdAt fields are managed by Supabase (DEFAULT now()), not writable by the ORM</li>
 * </ul>
 */
@ApplicationModule(
        displayName = "Garage"
)
package com.carsocialmedia.backend.garage;

import org.springframework.modulith.ApplicationModule;