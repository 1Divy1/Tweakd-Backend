/**
 * The Business module owns <strong>business accounts</strong> — real companies (repair shops,
 * car washes, tuning shops, dealerships, …) that appear as points on the app's virtual map and
 * have their own in-app profile page.
 *
 * <h2>Business accounts are not user accounts</h2>
 * A business account is a wholly separate identity type. It is <em>not</em> owned by an individual
 * {@code profiles} row and carries no {@code owner_id} — the two can exist independently. Businesses
 * are created and managed from a separate business dashboard, mirroring how staff accounts are
 * decoupled from app accounts. Consequently {@code business_accounts} has no link to
 * {@code auth.users} yet and this module is <strong>read-only</strong>: it serves the map and the
 * business profile screen. Business self-service login and writes will be added later.
 *
 * <h2>Visibility</h2>
 * Only businesses that are both {@code active_status = 'active'} and
 * {@code verification_status = 'verified'} are ever exposed. Pending submissions, rejected
 * applications, and suspended or deleted businesses are invisible to the app — enforced both here
 * (every query filters) and in Supabase RLS.
 *
 * <h2>Main API</h2>
 * Public interface: {@link com.tweakdapp.backend.business.BusinessService}
 * <ul>
 *   <li><strong>Map:</strong> findNearby — radius search around a centre point, nearest first</li>
 *   <li><strong>Profile:</strong> getBusiness — full detail including the weekly opening hours</li>
 *   <li><strong>Reference data:</strong> listTypes</li>
 * </ul>
 *
 * <h2>Opening hours</h2>
 * {@code is_open_now} is <strong>derived</strong> on every request from {@code business_hours} in the
 * business's own {@code timezone}; it is never stored, so it cannot go stale. See
 * {@link com.tweakdapp.backend.business.internal.OpeningHours}.
 *
 * <h2>Cross-module dependencies</h2>
 * None on other feature modules. {@code business_accounts.city} references the {@code cities}
 * reference table, which lives in {@code shared.geo} and is readable by any module. The only
 * module dependency is {@code storage}, used to turn the stored logo object key into a public URL.
 */
@ApplicationModule(
        displayName = "Business"
)
package com.tweakdapp.backend.business;

import org.springframework.modulith.ApplicationModule;
