package com.tweakdapp.backend.garage.internal.share;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Where a share code turns into a URL.
 *
 * <p>The database stores bare codes, so the origin lives here and nowhere else: pointing the
 * feature at a staging site, or moving the domain, is a config change rather than a data
 * migration. Note that codes already printed on stickers only survive such a move if the old
 * domain keeps redirecting.
 */
@Configuration
@ConfigurationProperties(prefix = "sharing")
@Getter
@Setter
public class SharingProperties {

    /** Origin + path prefix a code is appended to, no trailing slash. */
    private String publicBaseUrl = "https://web.tweakdapp.com/c";

    /** The full public URL for a code, e.g. {@code https://web.tweakdapp.com/c/7KQ3M9XA2F}. */
    public String urlFor(String code) {
        String base = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return base + "/" + code;
    }

    /**
     * What the QR code encodes: the same URL plus the source tag, so a scan can be told apart from
     * a tapped link without a second identifier or a redirect hop.
     */
    public String qrUrlFor(String code) {
        return urlFor(code) + "?s=qr";
    }
}
