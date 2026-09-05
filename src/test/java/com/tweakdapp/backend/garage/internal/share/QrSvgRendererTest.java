package com.tweakdapp.backend.garage.internal.share;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The renderer's output ends up printed and stuck to a car, so the test that matters is not that
 * the SVG looks plausible but that a scanner reading the drawn modules back gets the original URL.
 * The path is re-parsed into a bit matrix and decoded with ZXing to prove exactly that.
 */
class QrSvgRendererTest {

    private static final String URL = "https://web.tweakdapp.com/c/7KQ3M9XA2F?s=qr";

    private final QrSvgRenderer renderer = new QrSvgRenderer();

    /** The one that counts: what a phone camera pointed at the printed sticker would read. */
    @Test
    void theRenderedModulesDecodeBackToTheUrl() throws Exception {
        String svg = renderer.render(URL);

        BitMatrix matrix = parseSvg(svg);
        Result result = new QRCodeReader().decode(
                new BinaryBitmap(new GlobalHistogramBinarizer(new BitMatrixLuminanceSource(matrix))),
                Map.of(DecodeHintType.PURE_BARCODE, Boolean.TRUE));

        assertThat(result.getText()).isEqualTo(URL);
    }

    /** Same URL, same bytes — a reprint years later has to produce the sticker that already exists. */
    @Test
    void renderingIsDeterministic() {
        assertThat(renderer.render(URL)).isEqualTo(renderer.render(URL));
        assertThat(renderer.render(URL)).isNotEqualTo(renderer.render(URL + "x"));
    }

    /**
     * A square viewBox at least as big as the smallest QR version (21 modules) plus the 4-module
     * quiet zone on each side. Anything smaller means the margin hint was lost, and a symbol
     * without its quiet zone is one scanners skip over.
     */
    @Test
    void theSymbolIsSquareAndCarriesItsQuietZone() {
        String svg = renderer.render(URL);

        Matcher viewBox = Pattern.compile("viewBox=\"0 0 (\\d+) (\\d+)\"").matcher(svg);
        assertThat(viewBox.find()).isTrue();
        int width = Integer.parseInt(viewBox.group(1));
        int height = Integer.parseInt(viewBox.group(2));

        assertThat(width).isEqualTo(height);
        assertThat(width).isGreaterThanOrEqualTo(21 + 8);
    }

    /** White ground, black modules, and no anti-aliasing to blur the module edges when printed. */
    @Test
    void theDocumentIsPrintReady() {
        String svg = renderer.render(URL);

        assertThat(svg).startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\"");
        assertThat(svg).contains("shape-rendering=\"crispEdges\"");
        assertThat(svg).contains("fill=\"#ffffff\"");
        assertThat(svg).contains("fill=\"#000000\"");
        assertThat(svg.trim()).endsWith("</svg>");
    }

    @Test
    void renderBytesIsTheSameDocumentInUtf8() {
        assertThat(renderer.renderBytes(URL))
                .isEqualTo(renderer.render(URL).getBytes(StandardCharsets.UTF_8));
    }

    // ---- helpers ------------------------------------------------------------

    /** Rebuilds the module grid from the run-length encoded path, the inverse of the renderer. */
    private static BitMatrix parseSvg(String svg) {
        Matcher viewBox = Pattern.compile("viewBox=\"0 0 (\\d+) (\\d+)\"").matcher(svg);
        assertThat(viewBox.find()).isTrue();
        BitMatrix matrix = new BitMatrix(Integer.parseInt(viewBox.group(1)),
                Integer.parseInt(viewBox.group(2)));

        Matcher runs = Pattern.compile("M(\\d+) (\\d+)h(\\d+)v1h-\\3z").matcher(svg);
        while (runs.find()) {
            int x = Integer.parseInt(runs.group(1));
            int y = Integer.parseInt(runs.group(2));
            int run = Integer.parseInt(runs.group(3));
            for (int i = 0; i < run; i++) {
                matrix.set(x + i, y);
            }
        }
        return matrix;
    }

    /** Minimal LuminanceSource over a BitMatrix — ZXing's own lives in the AWT-bound `javase` jar. */
    private static final class BitMatrixLuminanceSource extends com.google.zxing.LuminanceSource {

        private final BitMatrix matrix;

        BitMatrixLuminanceSource(BitMatrix matrix) {
            super(matrix.getWidth(), matrix.getHeight());
            this.matrix = matrix;
        }

        @Override
        public byte[] getRow(int y, byte[] row) {
            if (row == null || row.length < getWidth()) {
                row = new byte[getWidth()];
            }
            for (int x = 0; x < getWidth(); x++) {
                row[x] = (byte) (matrix.get(x, y) ? 0 : 0xFF);
            }
            return row;
        }

        @Override
        public byte[] getMatrix() {
            byte[] pixels = new byte[getWidth() * getHeight()];
            for (int y = 0; y < getHeight(); y++) {
                for (int x = 0; x < getWidth(); x++) {
                    pixels[y * getWidth() + x] = (byte) (matrix.get(x, y) ? 0 : 0xFF);
                }
            }
            return pixels;
        }
    }
}
