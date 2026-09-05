package com.tweakdapp.backend.garage.exception;

import com.tweakdapp.backend.shared.exception.NotFoundException;

/**
 * 404: this share code has never existed — or the owner asked for a link on a car they have not
 * shared yet.
 *
 * <p>A malformed code raises this too rather than a 400. The code arrives from a URL somebody
 * scanned or typed; "not a valid code" and "no such code" are the same dead link to them, and
 * telling a scraper which of the two it hit only helps the scraper.
 */
public class ShareLinkNotFoundException extends NotFoundException {

    public ShareLinkNotFoundException() {
        super("This build is no longer shared on Tweakd.");
    }
}
