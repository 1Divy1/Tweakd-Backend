package com.carsocialmedia.backend.follow.exception;

import com.carsocialmedia.backend.shared.exception.NotFoundException;

public class FollowRequestNotFoundException extends NotFoundException {

    public FollowRequestNotFoundException(String requesterUsername) {
        super("No pending follow request from user '" + requesterUsername + "'");
    }
}
