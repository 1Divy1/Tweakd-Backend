package com.carsocialmedia.backend.profile.exception;

import com.carsocialmedia.backend.shared.exception.NotFoundException;

public class ProfileNotFoundException extends NotFoundException {

    private ProfileNotFoundException(String message) {
        super(message);
    }

    public static ProfileNotFoundException byUserId(String userId) {
        return new ProfileNotFoundException("Profile not found for user: " + userId);
    }

    public static ProfileNotFoundException byUsername(String username) {
        return new ProfileNotFoundException("Profile not found for username: " + username);
    }
}
