package com.tweakdapp.backend.garage.exception;

import com.tweakdapp.backend.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 410: the code is real but no longer serves a page — the owner paused sharing, the car changed
 * hands, or the owner has been banned.
 *
 * <p>Distinct from 404 for one audience only: crawlers and search engines, which drop a 410 from
 * their index and keep retrying a 404. Human visitors see the same "this build is no longer shared"
 * page either way.
 */
public class ShareLinkGoneException extends ApiException {

    public ShareLinkGoneException() {
        super(HttpStatus.GONE, "This build is no longer shared on Tweakd.");
    }
}
