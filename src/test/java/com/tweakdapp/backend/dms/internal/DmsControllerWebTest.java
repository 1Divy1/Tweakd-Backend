package com.tweakdapp.backend.dms.internal;

import com.tweakdapp.backend.dms.DmsService;
import com.tweakdapp.backend.dms.dto.DmConversationPageDto;
import com.tweakdapp.backend.dms.dto.DmMessageDto;
import com.tweakdapp.backend.dms.dto.DmMessagePageDto;
import com.tweakdapp.backend.dms.dto.DmReadReceiptDto;
import com.tweakdapp.backend.dms.dto.SendMessageRequest;
import com.tweakdapp.backend.dms.exception.CannotMessageSelfException;
import com.tweakdapp.backend.dms.exception.DmConversationNotFoundException;
import com.tweakdapp.backend.dms.exception.DmRecipientNotFoundException;
import com.tweakdapp.backend.garage.dto.CarSummaryDto;
import com.tweakdapp.backend.testsupport.AppWebMvcTest;
import com.tweakdapp.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The REST surface of DMs ({@link DmsController}): auth requirement, status codes, JWT-subject
 * delegation, request validation, snake_case DTO shapes, and the exception→status mapping
 * (not-your-conversation / not-your-message surface as the same 404, self-DM as 400).
 */
@AppWebMvcTest(DmsController.class)
class DmsControllerWebTest {

    private static final UUID CONV = UUID.fromString("00000000-0000-0000-0000-0000000000c0");
    private static final UUID PEER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DmsService dmsService;

    // ---- auth ---------------------------------------------------------------

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/dms/unread-count"))
                .andExpect(status().isUnauthorized());
    }

    // ---- GET /conversations -------------------------------------------------

    @Test
    void listConversationsReturnsThePageAndPassesTheJwtSubjectAndDefaults() throws Exception {
        when(dmsService.listConversations(eq(TestJwts.USER_ID), any(), eq(20)))
                .thenReturn(new DmConversationPageDto(List.of(), "next-cursor"));

        mockMvc.perform(get("/api/v1/dms/conversations").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.next_cursor").value("next-cursor"))
                .andExpect(jsonPath("$.items").isArray());

        verify(dmsService).listConversations(TestJwts.USER_ID, null, 20);
    }

    // ---- GET /conversations/{id}/messages -----------------------------------

    @Test
    void getMessagesSurfacesNotYourConversationAs404() throws Exception {
        when(dmsService.listMessages(eq(TestJwts.USER_ID), eq(CONV), any(), eq(30)))
                .thenThrow(new DmConversationNotFoundException(CONV));

        mockMvc.perform(get("/api/v1/dms/conversations/{id}/messages", CONV).with(TestJwts.user()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMessagesReturnsHistoryWithSnakeCaseWatermark() throws Exception {
        UUID msgId = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        UUID watermark = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
        UUID carId = UUID.fromString("00000000-0000-0000-0000-0000000000ca");
        CarSummaryDto car = new CarSummaryDto(carId, "BMW", "M3", null, null, null);
        when(dmsService.listMessages(eq(TestJwts.USER_ID), eq(CONV), any(), eq(30)))
                .thenReturn(new DmMessagePageDto(
                        List.of(new DmMessageDto(msgId, CONV, PEER, "hey", false,
                                Instant.parse("2026-07-16T10:00:00Z"), List.of(car))),
                        null, watermark));

        mockMvc.perform(get("/api/v1/dms/conversations/{id}/messages", CONV).with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peer_last_read_message_id").value(watermark.toString()))
                .andExpect(jsonPath("$.items[0].conversation_id").value(CONV.toString()))
                .andExpect(jsonPath("$.items[0].sender_id").value(PEER.toString()))
                .andExpect(jsonPath("$.items[0].deleted").value(false))
                .andExpect(jsonPath("$.items[0].tagged_cars[0].id").value(carId.toString()))
                .andExpect(jsonPath("$.items[0].tagged_cars[0].brand").value("BMW"))
                .andExpect(jsonPath("$.items[0].tagged_cars[0].model").value("M3"));
    }

    // ---- POST /messages -----------------------------------------------------

    @Test
    void sendMessageReturns201WithTheSavedMessage() throws Exception {
        UUID msgId = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        when(dmsService.sendMessage(eq(TestJwts.USER_ID), any(SendMessageRequest.class)))
                .thenReturn(new DmMessageDto(msgId, CONV, TestJwts.USER_ID, "hello", false,
                        Instant.parse("2026-07-16T10:00:00Z"), List.of()));

        mockMvc.perform(post("/api/v1/dms/messages")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient_id\":\"" + PEER + "\",\"content\":\"hello\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(msgId.toString()))
                .andExpect(jsonPath("$.sender_id").value(TestJwts.USER_ID.toString()))
                .andExpect(jsonPath("$.content").value("hello"))
                .andExpect(jsonPath("$.tagged_cars").isArray());

        ArgumentCaptor<SendMessageRequest> req = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(dmsService).sendMessage(eq(TestJwts.USER_ID), req.capture());
        assertThat(req.getValue().recipientId()).isEqualTo(PEER);
        assertThat(req.getValue().content()).isEqualTo("hello");
    }

    @Test
    void sendCarsOnlyMessageForwardsTaggedCarIdsAndReturns201() throws Exception {
        UUID msgId = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        UUID carId = UUID.fromString("00000000-0000-0000-0000-0000000000ca");
        CarSummaryDto car = new CarSummaryDto(carId, "BMW", "M3", null, null, null);
        when(dmsService.sendMessage(eq(TestJwts.USER_ID), any(SendMessageRequest.class)))
                .thenReturn(new DmMessageDto(msgId, CONV, TestJwts.USER_ID, "", false,
                        Instant.parse("2026-07-16T10:00:00Z"), List.of(car)));

        mockMvc.perform(post("/api/v1/dms/messages")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient_id\":\"" + PEER + "\",\"tagged_car_ids\":[\"" + carId + "\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value(""))
                .andExpect(jsonPath("$.tagged_cars[0].id").value(carId.toString()));

        ArgumentCaptor<SendMessageRequest> req = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(dmsService).sendMessage(eq(TestJwts.USER_ID), req.capture());
        assertThat(req.getValue().content()).isNull();
        assertThat(req.getValue().taggedCarIds()).containsExactly(carId);
    }

    @Test
    void sendMessageWithNeitherTextNorCarsSurfacesAs400() throws Exception {
        when(dmsService.sendMessage(eq(TestJwts.USER_ID), any(SendMessageRequest.class)))
                .thenThrow(new com.tweakdapp.backend.dms.exception.EmptyMessageException());

        mockMvc.perform(post("/api/v1/dms/messages")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient_id\":\"" + PEER + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendMessageWithTooManyTaggedCarsSurfacesAs400() throws Exception {
        when(dmsService.sendMessage(eq(TestJwts.USER_ID), any(SendMessageRequest.class)))
                .thenThrow(new com.tweakdapp.backend.dms.exception.TooManyTaggedCarsException(10));

        mockMvc.perform(post("/api/v1/dms/messages")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient_id\":\"" + PEER + "\",\"content\":\"hi\",\"tagged_car_ids\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendMessageWithUnknownTaggedCarSurfacesAs400() throws Exception {
        when(dmsService.sendMessage(eq(TestJwts.USER_ID), any(SendMessageRequest.class)))
                .thenThrow(new com.tweakdapp.backend.dms.exception.TaggedCarNotFoundException());

        mockMvc.perform(post("/api/v1/dms/messages")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient_id\":\"" + PEER + "\",\"tagged_car_ids\":[\""
                                + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankContentIsNoLongerRejectedByValidationAndReachesTheService() throws Exception {
        // content is now optional (a car-only message is valid), so blank content no longer
        // short-circuits at bean validation; the "text or cars" rule is enforced in the service.
        when(dmsService.sendMessage(eq(TestJwts.USER_ID), any(SendMessageRequest.class)))
                .thenThrow(new com.tweakdapp.backend.dms.exception.EmptyMessageException());

        mockMvc.perform(post("/api/v1/dms/messages")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient_id\":\"" + PEER + "\",\"content\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verify(dmsService).sendMessage(eq(TestJwts.USER_ID), any());
    }

    @Test
    void sendMessageWithMissingRecipientIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/dms/messages")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\"}"))
                .andExpect(status().isBadRequest());

        verify(dmsService, never()).sendMessage(any(), any());
    }

    @Test
    void sendMessageWithOverlongContentIsRejected() throws Exception {
        String tooLong = "x".repeat(2001);
        mockMvc.perform(post("/api/v1/dms/messages")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient_id\":\"" + PEER + "\",\"content\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());

        verify(dmsService, never()).sendMessage(any(), any());
    }

    @Test
    void sendMessageToYourselfSurfacesAs400() throws Exception {
        when(dmsService.sendMessage(eq(TestJwts.USER_ID), any(SendMessageRequest.class)))
                .thenThrow(new CannotMessageSelfException());

        mockMvc.perform(post("/api/v1/dms/messages")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient_id\":\"" + TestJwts.USER_ID + "\",\"content\":\"hi\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendMessageToUnknownRecipientSurfacesAs404() throws Exception {
        when(dmsService.sendMessage(eq(TestJwts.USER_ID), any(SendMessageRequest.class)))
                .thenThrow(new DmRecipientNotFoundException(PEER));

        mockMvc.perform(post("/api/v1/dms/messages")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient_id\":\"" + PEER + "\",\"content\":\"hi\"}"))
                .andExpect(status().isNotFound());
    }

    // ---- POST /conversations/{id}/read --------------------------------------

    @Test
    void markReadReturnsTheWatermark() throws Exception {
        UUID watermark = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
        when(dmsService.markRead(TestJwts.USER_ID, CONV)).thenReturn(new DmReadReceiptDto(CONV, watermark));

        mockMvc.perform(post("/api/v1/dms/conversations/{id}/read", CONV).with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation_id").value(CONV.toString()))
                .andExpect(jsonPath("$.last_read_message_id").value(watermark.toString()));
    }

    // ---- POST /conversations/{id}/hide --------------------------------------

    @Test
    void hideConversationReturns204AndDelegates() throws Exception {
        mockMvc.perform(post("/api/v1/dms/conversations/{id}/hide", CONV).with(TestJwts.user()))
                .andExpect(status().isNoContent());

        verify(dmsService).hideConversation(TestJwts.USER_ID, CONV);
    }

    // ---- DELETE /messages/{id} ----------------------------------------------

    @Test
    void deleteMessageReturns204AndDelegates() throws Exception {
        UUID msgId = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

        mockMvc.perform(delete("/api/v1/dms/messages/{id}", msgId).with(TestJwts.user()))
                .andExpect(status().isNoContent());

        verify(dmsService).deleteMessage(TestJwts.USER_ID, msgId);
    }

    @Test
    void deleteMessageThatIsNotYoursSurfacesAs404() throws Exception {
        UUID msgId = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        doThrow(new com.tweakdapp.backend.dms.exception.DmMessageNotFoundException(msgId))
                .when(dmsService).deleteMessage(TestJwts.USER_ID, msgId);

        mockMvc.perform(delete("/api/v1/dms/messages/{id}", msgId).with(TestJwts.user()))
                .andExpect(status().isNotFound());
    }

    // ---- GET /unread-count --------------------------------------------------

    @Test
    void unreadCountReturnsTheBadgeNumber() throws Exception {
        when(dmsService.countUnread(TestJwts.USER_ID)).thenReturn(7L);

        mockMvc.perform(get("/api/v1/dms/unread-count").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(7));
    }
}
