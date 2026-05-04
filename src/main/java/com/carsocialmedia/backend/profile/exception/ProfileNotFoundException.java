package com.carsocialmedia.backend.profile.exception;

import com.carsocialmedia.backend.shared.exception.NotFoundException;

public class ProfileNotFoundException extends NotFoundException {

    public ProfileNotFoundException(String userId) {
        super("Profile not found for user: " + userId);
    }
}
