package com.tweakdapp.backend.mapevents.exception;

import com.tweakdapp.backend.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 410 on the public event page: the event was approved and shared, then cancelled.
 *
 * <p>Distinct from {@link MapEventNotFoundException} for crawlers only — a 410 is dropped from an
 * index, a 404 is retried. The website shows the visitor the same "no longer available" screen
 * either way. Only ever thrown for an event that was once public, so it reveals nothing a
 * visitor could not already have seen.
 */
public class MapEventGoneException extends ApiException {

    public MapEventGoneException() {
        super(HttpStatus.GONE, "This event is no longer available on Tweakd.");
    }
}
