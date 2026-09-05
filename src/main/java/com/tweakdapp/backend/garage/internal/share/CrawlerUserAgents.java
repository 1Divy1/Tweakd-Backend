package com.tweakdapp.backend.garage.internal.share;

/**
 * Whether a request to the public car page came from a link-preview crawler rather than a person.
 *
 * <p>The whole point of the share link is that it unfurls: paste it into WhatsApp and the chat
 * shows the car's photo. Every one of those unfurls is a fetch of the public endpoint, and a
 * message sent to a group of forty produces forty of them before a single human has tapped
 * anything. Counting those would make the owner-facing "viewed 200 times" number meaningless.
 *
 * <p>This only ever gates the counter. The response itself is identical either way — a crawler that
 * lied about its user agent still gets the page, it just also gets counted.
 */
public final class CrawlerUserAgents {

    /**
     * Matched case-insensitively as substrings. Covers the platforms a car link is actually shared
     * on plus the search engines that will find the pages; anything unrecognised counts as a
     * person, which is the safe direction to be wrong in for an analytics number.
     */
    private static final String[] TOKENS = {
            "facebookexternalhit",
            "whatsapp",
            "twitterbot",
            "telegrambot",
            "slackbot",
            "discordbot",
            "linkedinbot",
            "googlebot",
            "bingbot",
            "applebot",
            "pinterest",
            "redditbot",
            "embedly",
            "iframely",
            "skypeuripreview",
            "vkshare",
            "developers.google.com/+/web/snippet",
    };

    private CrawlerUserAgents() {
    }

    /** True if this user agent is a known link-preview crawler. Null / blank is treated as a person. */
    public static boolean matches(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return false;
        }
        String lower = userAgent.toLowerCase();
        for (String token : TOKENS) {
            if (lower.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
