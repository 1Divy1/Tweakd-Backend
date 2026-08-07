/**
 * Geographic reference data: the {@code countries} and {@code cities} lookup tables, plus the
 * helpers for the {@code geography(Point,4326)} columns used across the app.
 *
 * <p>This is app-wide reference data, not the property of any one feature. It originally lived
 * inside the {@code profile} module because onboarding was the first thing to need a city list;
 * once the {@code business} module needed the same table to render a business's city, that
 * ownership stopped making sense — a business has nothing to do with a user profile.
 *
 * <p>Living in {@code shared} (an OPEN Modulith module) means any module may read cities and
 * countries directly, without one feature module having to expose a lookup on behalf of another.
 *
 * <p>Rows are seeded and owned by Supabase; nothing in the app writes to these tables.
 *
 * <p>Note that {@code cities.id} is a slug ({@code cluj-napoca}, {@code bucharest}) and is not
 * displayable — {@code cities.name} carries the properly accented display name
 * ({@code Cluj-Napoca}, {@code București}). Anything storing a city id and showing it to a user
 * must resolve the name through {@link com.carsocialmedia.backend.shared.geo.CityRepository}.
 */
package com.carsocialmedia.backend.shared.geo;
