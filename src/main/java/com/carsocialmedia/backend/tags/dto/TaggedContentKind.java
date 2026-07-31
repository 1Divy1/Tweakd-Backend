package com.carsocialmedia.backend.tags.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Which surface a tag lives on. Doubles as the client's rendering discriminator on every item of
 * the tags feed and as the path segment of the untag endpoint, so the wire form is the lowercase
 * snake_case name rather than the enum constant.
 */
public enum TaggedContentKind {

    /** A classic post. */
    POST("post"),

    /** A comment (or threaded reply) on a classic post. */
    POST_COMMENT("post_comment"),

    /** A forum thread's opening post. */
    FORUM_THREAD("forum_thread"),

    /** A reply inside a forum thread. */
    FORUM_REPLY("forum_reply");

    private final String wireName;

    TaggedContentKind(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    /**
     * Parses the wire form, case-insensitively. Returns {@code null} for an unknown value so the
     * caller can decide the error shape (the controller turns it into a 400).
     */
    @JsonCreator
    public static TaggedContentKind fromWireName(String value) {
        if (value == null) {
            return null;
        }
        for (TaggedContentKind kind : values()) {
            if (kind.wireName.equalsIgnoreCase(value) || kind.name().equalsIgnoreCase(value)) {
                return kind;
            }
        }
        return null;
    }
}
