package com.carsocialmedia.backend.profile.event;

import java.util.UUID;

/**
 * Domain event published when a profile transitions from private to public.
 * Other modules (e.g. {@code follow}) can consume it to react accordingly —
 * for instance, auto-accepting all pending follow requests targeted at the
 * now-public user.
 */
public record ProfileBecamePublicEvent(UUID userId) {}
