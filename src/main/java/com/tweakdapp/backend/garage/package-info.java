/**
 * The Garage module manages user car collections and modifications.
 *
 * <h2>Overview</h2>
 * Each user has one garage containing cars they own. Cars can have detailed specifications
 * (year, horsepower, weight, etc.) and a list of modifications (upgrades, customizations).
 * Modifications track before/after images, installation date, and optionally the cost.
 *
 * <h2>Privacy Model</h2>
 * All accounts are public: any authenticated user can read any garage and any car. There is no
 * follower gate and no per-profile privacy flag.
 *
 * <p>Car sharing splits that in two. The <strong>share surface</strong> — whether a car is shared,
 * its code, its QR and its counters — is owner-only, so a non-owner cannot even discover that a car
 * is shared. The <strong>public read</strong> ({@code GET /public/v1/cars/{code}}) is
 * unauthenticated and open to anyone holding the code, but it is served through a hand-written
 * projection ({@link com.tweakdapp.backend.garage.dto.PublicCarDto}) rather than the in-app
 * {@code CarDto}: a field added to the car reaches the open internet only if somebody deliberately
 * adds it to the projection too. It is refused with 410 when the link is paused or revoked, or when
 * its owner is banned.
 *
 * <h2>Main API</h2>
 * Public interface: {@link com.tweakdapp.backend.garage.GarageService}
 * <ul>
 *   <li><strong>Garage Views:</strong> getMyGarage, getGarageByUsername</li>
 *   <li><strong>Cars:</strong> addCar, updateCar, deleteCar, getCar</li>
 *   <li><strong>Modifications:</strong> addModification, updateModification, deleteModification</li>
 *   <li><strong>Reference Data:</strong> listBrands, listModelsByBrand, listDrivetrains, listColors, listDistanceUnits, listStatusOptions, listModCategories</li>
 *   <li><strong>Share links:</strong> ensureShareLink, getShareLink, setShareLinkEnabled,
 *       renderShareQrSvg, revokeShareLinksForCar, resolveShareCode, getPublicCar</li>
 * </ul>
 *
 * <h2>Database Constraints</h2>
 * <ul>
 *   <li>Brand/model consistency: model.brand_id must equal the selected brand</li>
 * *   <li>Cascading deletes: deleting a car cascades to delete its modifications</li>
 *   <li>createdAt fields are managed by Supabase (DEFAULT now()), not writable by the ORM</li>
 *   <li>A share code is unique across every row ever issued, revoked ones included, and a car has
 *       at most one live link — both enforced by indexes, not by service pre-checks</li>
 * </ul>
 */
@ApplicationModule(
        displayName = "Garage"
)
package com.tweakdapp.backend.garage;

import org.springframework.modulith.ApplicationModule;