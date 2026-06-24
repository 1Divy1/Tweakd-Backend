package com.carsocialmedia.backend.relationships.exception;

import com.carsocialmedia.backend.shared.exception.NotFoundException;

public class FollowRequestNotFoundException extends NotFoundException {

    public FollowRequestNotFoundException(String requesterUsername) {
        super("No pending follow request from user '" + requesterUsername + "'");
    }
}
