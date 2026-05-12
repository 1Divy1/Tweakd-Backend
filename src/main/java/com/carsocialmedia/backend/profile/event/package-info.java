/**
 * Profile domain events exposed as a named interface so other modules
 * (e.g. {@code follow}) can listen to them without crossing into
 * {@code profile.internal}.
 */
@NamedInterface("events")
package com.carsocialmedia.backend.profile.event;

import org.springframework.modulith.NamedInterface;