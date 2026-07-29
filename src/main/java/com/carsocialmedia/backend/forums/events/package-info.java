/**
 * Forums domain events exposed as a named interface so the {@code notification} module can listen
 * for them without crossing into {@code forums.internal}. They carry ids only — never entities or
 * DTOs — and nothing in this package depends back on the consumer.
 */
@NamedInterface("events")
package com.carsocialmedia.backend.forums.events;

import org.springframework.modulith.NamedInterface;
