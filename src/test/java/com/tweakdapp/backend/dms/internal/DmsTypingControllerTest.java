package com.tweakdapp.backend.dms.internal;

import com.tweakdapp.backend.dms.DmsService;
import com.tweakdapp.backend.dms.dto.TypingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The one client→server socket destination ({@link DmsTypingController}): it drops pings with no
 * principal or no conversation id and otherwise relays with the principal name (the JWT subject)
 * as the typist id.
 */
class DmsTypingControllerTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CONV = UUID.fromString("00000000-0000-0000-0000-0000000000c0");

    private DmsService dmsService;
    private DmsTypingController controller;

    @BeforeEach
    void setUp() {
        dmsService = mock(DmsService.class);
        controller = new DmsTypingController(dmsService);
    }

    @Test
    void relaysTypingUsingThePrincipalNameAsTheTypistId() {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(USER.toString());

        controller.typing(principal, new TypingRequest(CONV, true));

        verify(dmsService).relayTyping(USER, CONV, true);
    }

    @Test
    void dropsPingsWithNoPrincipal() {
        controller.typing(null, new TypingRequest(CONV, true));

        verifyNoInteractions(dmsService);
    }

    @Test
    void dropsPingsWithNoConversationId() {
        Principal principal = mock(Principal.class);

        controller.typing(principal, new TypingRequest(null, true));

        verifyNoInteractions(dmsService);
    }
}
