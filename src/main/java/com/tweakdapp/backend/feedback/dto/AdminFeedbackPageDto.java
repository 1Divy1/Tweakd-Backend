package com.tweakdapp.backend.feedback.dto;

import java.util.List;

/** One keyset page of the admin dashboard's feedback list. */
public record AdminFeedbackPageDto(
        List<AdminFeedbackDto> items,
        String nextCursor
) {}
