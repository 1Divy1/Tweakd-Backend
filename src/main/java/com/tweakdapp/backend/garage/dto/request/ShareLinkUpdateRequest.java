package com.tweakdapp.backend.garage.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Pause or resume a car's share link.
 *
 * <p>The only field there is, on purpose. The code itself is not editable and cannot be
 * regenerated: it may already be printed on a sticker glued to the car, and handing the owner a
 * button that silently invalidates that is a foot-gun, not a feature. Pausing turns the public page
 * off while keeping the code reserved, so resuming brings the same sticker back to life.
 *
 * @param enabled true to publish the link, false to pause it
 */
public record ShareLinkUpdateRequest(@NotNull Boolean enabled) {}
