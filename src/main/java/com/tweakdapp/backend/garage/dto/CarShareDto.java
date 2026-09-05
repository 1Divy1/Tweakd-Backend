package com.tweakdapp.backend.garage.dto;

import java.time.Instant;

/**
 * A car's share link, as its owner sees it. Owner-only: nobody else can discover that a car is
 * shared, or what its code is, through the API.
 *
 * @param code         the public code, canonical uppercase (e.g. {@code 7KQ3M9XA2F})
 * @param url          the shareable URL. The client appends its own {@code ?s=<channel>} tag per
 *                     share target, which is why this one carries none
 * @param qrUrl        what the QR code encodes: {@code url} + {@code ?s=qr}
 * @param enabled      false = paused by the owner; the public page answers 410 but the code, and
 *                     therefore any printed sticker, stays valid and can be resumed
 * @param createdAt    when the link was first minted
 * @param viewCount    counted visits that were not QR scans (crawler previews excluded)
 * @param qrScanCount  counted visits that carried {@code ?s=qr}
 * @param lastViewedAt the most recent counted visit, or null if nobody has opened it yet
 */
public record CarShareDto(
        String code,
        String url,
        String qrUrl,
        boolean enabled,
        Instant createdAt,
        long viewCount,
        long qrScanCount,
        Instant lastViewedAt
) {}
