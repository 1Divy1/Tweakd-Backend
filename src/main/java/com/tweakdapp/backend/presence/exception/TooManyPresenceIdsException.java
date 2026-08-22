package com.tweakdapp.backend.presence.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/** The batch presence lookup is capped; ask in pages instead. */
public class TooManyPresenceIdsException extends BadRequestException {

    public TooManyPresenceIdsException(int max) {
        super("At most " + max + " user ids may be queried at once");
    }
}
