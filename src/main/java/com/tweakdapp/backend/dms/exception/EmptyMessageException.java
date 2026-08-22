package com.tweakdapp.backend.dms.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/** A DM must carry text or at least one tagged car — an entirely empty message is rejected. */
public class EmptyMessageException extends BadRequestException {

    public EmptyMessageException() {
        super("A message must have text or at least one tagged car");
    }
}
