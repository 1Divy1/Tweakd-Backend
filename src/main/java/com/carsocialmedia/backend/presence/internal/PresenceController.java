package com.carsocialmedia.backend.presence.internal;

import com.carsocialmedia.backend.presence.PresenceService;
import com.carsocialmedia.backend.presence.dto.PresenceDto;
import com.carsocialmedia.backend.presence.exception.TooManyPresenceIdsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/presence")
class PresenceController {

    private static final int MAX_IDS = 100;

    private final PresenceService presenceService;

    PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    /** Batch presence lookup, e.g. {@code ?user_ids=<uuid>,<uuid>}. Visible to any authenticated user. */
    @GetMapping
    public List<PresenceDto> getPresence(@RequestParam("user_ids") List<UUID> userIds) {
        if (userIds.size() > MAX_IDS) {
            throw new TooManyPresenceIdsException(MAX_IDS);
        }
        return List.copyOf(presenceService.getPresence(userIds).values());
    }
}
