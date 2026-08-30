package com.tweakdapp.backend.dms.internal;

import com.tweakdapp.backend.dms.DmsService;
import com.tweakdapp.backend.dms.dto.TypingRequest;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * The one client-to-server socket destination: typing indicators, sent to {@code /app/dms/typing}.
 * The principal comes from the STOMP CONNECT authentication
 * ({@code shared/realtime/JwtChannelInterceptor}); its name is the JWT subject, i.e. the user's
 * {@code profiles.id}. Malformed or foreign conversation ids are dropped silently — typing is
 * best-effort by design.
 */
@Controller
class DmsTypingController {

    private final DmsService dmsService;

    DmsTypingController(DmsService dmsService) {
        this.dmsService = dmsService;
    }

    @MessageMapping("/dms/typing")
    public void typing(Principal principal, @Payload TypingRequest request) {
        if (principal == null || request.conversationId() == null) {
            return;
        }
        dmsService.relayTyping(UUID.fromString(principal.getName()), request.conversationId(), request.typing());
    }
}
