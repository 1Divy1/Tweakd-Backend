package com.tweakdapp.backend.posts.exception;

import com.tweakdapp.backend.shared.exception.NotFoundException;

import java.util.UUID;

/**
 * Thrown when sharing a build-log modification that does not exist or sits on a car the caller does
 * not own. One 404 for both, so another user's build log cannot be probed for by id.
 */
public class ModificationNotFoundException extends NotFoundException {

    public ModificationNotFoundException(UUID modificationId) {
        super("No modification " + modificationId + " on a car owned by the caller");
    }
}
