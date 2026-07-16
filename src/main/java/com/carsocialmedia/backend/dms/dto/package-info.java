/**
 * DM DTOs. Exposed as a named interface so other modules could consume them without crossing into
 * {@code dms.internal}. {@code DmSocketEvent} is also the wire format of the {@code /user/queue/dms}
 * WebSocket queue.
 */
@NamedInterface("dto")
package com.carsocialmedia.backend.dms.dto;

import org.springframework.modulith.NamedInterface;
