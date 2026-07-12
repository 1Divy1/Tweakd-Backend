package com.carsocialmedia.backend.shared.staff;

import java.util.UUID;

/**
 * A lightweight reference to a dashboard staff member (see {@link StaffDirectory}). Staff are
 * <em>not</em> app users — they have no {@code profiles} row — so wherever content modules used to
 * render a staff actor as a profile card, they render this instead.
 */
public record StaffRefDto(
        UUID id,
        String displayName,
        String avatarUrl
) {}
