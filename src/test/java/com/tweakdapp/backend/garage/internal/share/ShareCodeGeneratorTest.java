package com.tweakdapp.backend.garage.internal.share;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two halves of the share code's contract, both of which end up on a physical sticker:
 * what {@code next()} is allowed to produce, and what {@code normalize()} has to forgive when
 * somebody reads that sticker back off a car and types it in.
 */
class ShareCodeGeneratorTest {

    private final ShareCodeGenerator generator = new ShareCodeGenerator();

    // ---- generation ---------------------------------------------------------

    /**
     * The ambiguous characters are the point of the alphabet. If a generated code could contain an
     * O, normalize would fold it to a 0 and the typed-in code would no longer match the stored one.
     */
    @Test
    void generatedCodesUseOnlyTheUnambiguousAlphabet() {
        for (int i = 0; i < 10_000; i++) {
            String code = generator.next();
            assertThat(code).hasSize(ShareCodeGenerator.LENGTH);
            assertThat(code).matches("[0-9A-HJKMNP-TV-Z]{10}");
            assertThat(code).doesNotContainAnyWhitespaces();
        }
    }

    /** Not a randomness proof — just that it is not returning a constant. */
    @Test
    void generatedCodesAreNotRepeated() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            codes.add(generator.next());
        }
        assertThat(codes).hasSize(10_000);
    }

    /** Whatever it produces has to survive the round trip through the lookup path unchanged. */
    @Test
    void generatedCodesAreAlreadyCanonical() {
        for (int i = 0; i < 1_000; i++) {
            String code = generator.next();
            assertThat(ShareCodeGenerator.normalize(code)).contains(code);
        }
    }

    // ---- normalization ------------------------------------------------------

    @Test
    void normalizeUppercases() {
        assertThat(ShareCodeGenerator.normalize("7kq3m9xa2f")).contains("7KQ3M9XA2F");
    }

    /** The whole reason those letters are absent from the alphabet. */
    @Test
    void normalizeFoldsTheLookalikeLetters() {
        assertThat(ShareCodeGenerator.normalize("O123456789")).contains("0123456789");
        assertThat(ShareCodeGenerator.normalize("I123456789")).contains("1123456789");
        assertThat(ShareCodeGenerator.normalize("L123456789")).contains("1123456789");
        assertThat(ShareCodeGenerator.normalize("oil2345678")).contains("0112345678");
    }

    /** The app shows the code grouped (7KQ3-M9XA-2F) so it can be read aloud; that must paste back. */
    @Test
    void normalizeStripsGroupingAndWhitespace() {
        assertThat(ShareCodeGenerator.normalize("7KQ3-M9XA-2F")).contains("7KQ3M9XA2F");
        assertThat(ShareCodeGenerator.normalize("  7KQ3 M9XA 2F ")).contains("7KQ3M9XA2F");
    }

    @Test
    void normalizeIsIdempotent() {
        Optional<String> once = ShareCodeGenerator.normalize("7kq3-m9xa-2f");
        assertThat(once).isPresent();
        assertThat(ShareCodeGenerator.normalize(once.get())).isEqualTo(once);
    }

    @Test
    void normalizeRejectsWrongLengths() {
        assertThat(ShareCodeGenerator.normalize("7KQ3M9XA2")).isEmpty();
        assertThat(ShareCodeGenerator.normalize("7KQ3M9XA2FF")).isEmpty();
        assertThat(ShareCodeGenerator.normalize("")).isEmpty();
    }

    @Test
    void normalizeRejectsCharactersOutsideTheAlphabet() {
        assertThat(ShareCodeGenerator.normalize("7KQ3M9XA2!")).isEmpty();
        assertThat(ShareCodeGenerator.normalize("../../etc/pa")).isEmpty();
        assertThat(ShareCodeGenerator.normalize("UUUUUUUUUU")).isEmpty();  // U is not in the alphabet
    }

    @Test
    void normalizeRejectsNull() {
        assertThat(ShareCodeGenerator.normalize(null)).isEmpty();
    }
}
