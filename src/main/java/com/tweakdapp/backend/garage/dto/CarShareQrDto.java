package com.tweakdapp.backend.garage.dto;

/**
 * A rendered QR code and the code it encodes, returned together so the controller can name the
 * download {@code tweakd-{code}.svg} without a second ownership-checked round trip on a
 * two-connection pool.
 *
 * @param code the share code the QR points at
 * @param svg  the SVG document, UTF-8 encoded
 */
public record CarShareQrDto(String code, byte[] svg) {}
