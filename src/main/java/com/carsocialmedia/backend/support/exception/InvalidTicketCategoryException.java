package com.carsocialmedia.backend.support.exception;

import com.carsocialmedia.backend.shared.exception.BadRequestException;

/** The category doesn't reference a {@code support_ticket_categories} row. */
public class InvalidTicketCategoryException extends BadRequestException {

    public InvalidTicketCategoryException(String category) {
        super("Invalid ticket category: " + category);
    }
}
