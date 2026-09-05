package com.tweakdapp.backend.garage.internal.share;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/**
 * Renders a share URL as a QR code in SVG.
 *
 * <p>Vector, and rendered on the server, for two reasons. A sticker gets printed at whatever size
 * its owner feels like, and only a vector survives that; and one renderer means every device — and
 * every reprint years later — produces byte-identical artwork, which an on-device library could
 * not promise. ZXing's {@code core} artifact is used alone: the {@code javase} companion encodes
 * through {@code java.awt.image}, which the slim JRE in the Cloud Run image does not ship.
 *
 * <p>Error correction is level <strong>H</strong> (~30% of the symbol can be damaged and still
 * decode). That is the level a physical sticker actually needs — scratches, road grime, a bad
 * angle — and it leaves room to overlay a centre logo later without re-encoding anything.
 */
@Component
public class QrSvgRenderer {

    /** Quiet zone in modules. Four is the QR specification's minimum; below it, scanners miss the symbol. */
    private static final int MARGIN_MODULES = 4;

    /**
     * Encodes {@code text} and returns a standalone SVG document.
     *
     * <p>The result is deterministic for a given input: same URL, same bytes. The symbol is drawn
     * as a single {@code <path>} whose dark modules are run-length encoded per row, rather than one
     * {@code <rect>} per module — a ~1 kB file instead of ~40 kB, and no hairline seams between
     * adjacent modules when a printer rounds coordinates.
     */
    public String render(String text) {
        BitMatrix matrix = encode(text);

        int width = matrix.getWidth();
        int height = matrix.getHeight();

        StringBuilder path = new StringBuilder();
        for (int y = 0; y < height; y++) {
            int x = 0;
            while (x < width) {
                if (!matrix.get(x, y)) {
                    x++;
                    continue;
                }
                int run = 0;
                while (x + run < width && matrix.get(x + run, y)) {
                    run++;
                }
                path.append('M').append(x).append(' ').append(y)
                        .append('h').append(run)
                        .append("v1h-").append(run)
                        .append('z');
                x += run;
            }
        }

        return """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" width="%d" height="%d" \
                shape-rendering="crispEdges" role="img" aria-label="Tweakd car share QR code">\
                <rect width="%d" height="%d" fill="#ffffff"/>\
                <path fill="#000000" d="%s"/>\
                </svg>
                """.formatted(width, height, width, height, width, height, path);
    }

    /** {@link #render} as UTF-8 bytes, which is what the controller streams and the app saves. */
    public byte[] renderBytes(String text) {
        return render(text).getBytes(StandardCharsets.UTF_8);
    }

    private BitMatrix encode(String text) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.MARGIN, MARGIN_MODULES);
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        try {
            // 0x0 asks ZXing for the symbol at its natural module size — one matrix cell per
            // module. Scaling is the SVG viewBox's job, not the encoder's.
            return new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints);
        } catch (WriterException e) {
            // Only thrown when the payload cannot fit any QR version. Our payload is a ~40-character
            // URL, so this is a programming error rather than something a caller can handle.
            throw new IllegalStateException("Could not encode QR code for: " + text, e);
        }
    }
}
