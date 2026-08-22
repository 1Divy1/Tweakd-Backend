package com.tweakdapp.backend.dms.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/** One or more of the cars a DM tried to tag does not exist. */
public class TaggedCarNotFoundException extends BadRequestException {

    public TaggedCarNotFoundException() {
        super("One or more tagged cars do not exist");
    }
}
