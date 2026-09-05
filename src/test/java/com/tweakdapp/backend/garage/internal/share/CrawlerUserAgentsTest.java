package com.tweakdapp.backend.garage.internal.share;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which fetches of the public page count as a person. The bias is deliberate and one-directional:
 * an unrecognised agent counts, because inflating an owner's view number with a few stray bots is a
 * smaller lie than hiding real visits behind an over-eager filter.
 */
class CrawlerUserAgentsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)",
            "WhatsApp/2.23.20.0 A",
            "Twitterbot/1.0",
            "TelegramBot (like TwitterBot)",
            "Slackbot-LinkExpanding 1.0 (+https://api.slack.com/robots)",
            "Mozilla/5.0 (compatible; Discordbot/2.0; +https://discordapp.com)",
            "LinkedInBot/1.0 (compatible; Mozilla/5.0; Jakarta Commons-HttpClient/3.1)",
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
            "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15 Applebot/0.1",
            "Pinterest/0.2 (+http://www.pinterest.com/bot.html)",
            "redditbot/1.0",
    })
    void knownLinkPreviewCrawlersAreNotCounted(String userAgent) {
        assertThat(CrawlerUserAgents.matches(userAgent)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            "Dart/3.5 (dart:io)",
    })
    void realVisitorsAreCounted(String userAgent) {
        assertThat(CrawlerUserAgents.matches(userAgent)).isFalse();
    }

    /** Matching is case-insensitive: crawlers do not agree on how to spell their own names. */
    @Test
    void matchingIgnoresCase() {
        assertThat(CrawlerUserAgents.matches("WHATSAPP/2.0")).isTrue();
        assertThat(CrawlerUserAgents.matches("googlebot")).isTrue();
    }

    /** A request with no user agent is a person until proven otherwise. */
    @Test
    void missingUserAgentCounts() {
        assertThat(CrawlerUserAgents.matches(null)).isFalse();
        assertThat(CrawlerUserAgents.matches("")).isFalse();
        assertThat(CrawlerUserAgents.matches("   ")).isFalse();
    }
}
