package com.tweakdapp.backend.garage.internal.share;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Optional;

/**
 * Mints and canonicalises the public share code in {@code https://web.tweakdapp.com/c/{code}}.
 *
 * <p>Ten characters of <strong>Crockford base32</strong> — the digits and the letters minus
 * {@code I}, {@code L}, {@code O} and {@code U}. Two reasons for that alphabet rather than a UUID
 * or a slug:
 *
 * <ul>
 *   <li>The code is printed. Somebody will eventually read one off a scratched sticker and type it
 *       in, and the excluded letters are exactly the ones they would get wrong.
 *       {@link #normalize} folds those mistakes back ({@code O → 0}, {@code I}/{@code L → 1}), so a
 *       mistyped code still resolves instead of 404ing.</li>
 *   <li>Ten characters is ~50 bits: not enumerable by anyone scraping for other people's builds,
 *       and short enough to keep the QR sparse — fewer modules means it still scans when printed
 *       small or from across a car park.</li>
 * </ul>
 *
 * <p>The code carries no meaning. It does not encode the car, the owner or the date, so nothing
 * the user can rename later invalidates a sticker that is already glued to a bumper.
 */
@Component
public class ShareCodeGenerator {

    /** Crockford base32: 0-9 A-H J-K M-N P-T V-Z. No I, L, O or U. */
    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    /** Code length in characters. 32^10 ≈ 1.1e15 possible codes. */
    public static final int LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    /** A fresh candidate code. Uniqueness is the database's job, not this method's. */
    public String next() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Canonical form of a code that came in from outside — a URL path, a typed-in code, a scanned
     * one. Uppercases, drops dashes and whitespace (people group long codes), and folds the
     * ambiguous letters onto the digits they look like.
     *
     * @return the canonical code, or empty if what is left is not exactly {@link #LENGTH}
     *         characters of the alphabet. Callers turn empty into a 404: the code is public input,
     *         and "malformed" and "unknown" are the same dead-link page to the person holding it.
     */
    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < raw.length(); i++) {
            char c = Character.toUpperCase(raw.charAt(i));
            if (c == '-' || Character.isWhitespace(c)) {
                continue;
            }
            char folded = switch (c) {
                case 'O' -> '0';
                case 'I', 'L' -> '1';
                default -> c;
            };
            if (ALPHABET.indexOf(folded) < 0) {
                return Optional.empty();
            }
            if (sb.length() == LENGTH) {
                return Optional.empty();  // too long; bail without scanning the rest
            }
            sb.append(folded);
        }
        return sb.length() == LENGTH ? Optional.of(sb.toString()) : Optional.empty();
    }
}
