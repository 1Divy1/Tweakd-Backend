package com.tweakdapp.backend.garage.dto;

/**
 * Where a visit to a share link came from, parsed from the {@code ?s=} tag on the URL.
 *
 * <p>Only the QR code is told apart in v1, because it is the only channel whose number answers a
 * question nobody can answer otherwise: is the printed sticker doing anything? Every other tag the
 * app appends ({@code wa}, {@code tg}, {@code x}, {@code sms}, {@code ig}, {@code copy}) collapses
 * to {@link #LINK} here and is stored in the same counter. The app tags them from day one anyway,
 * so per-channel numbers become one migration rather than a client release.
 */
public enum ShareSource {

    /** A tapped link — or an untagged, unrecognised or absent {@code ?s=}. */
    LINK,

    /** A scan of the printed QR code ({@code ?s=qr}). */
    QR;

    /** Parses the raw {@code ?s=} value. Anything that is not {@code qr} is a link. */
    public static ShareSource fromTag(String tag) {
        return tag != null && tag.equalsIgnoreCase("qr") ? QR : LINK;
    }
}
