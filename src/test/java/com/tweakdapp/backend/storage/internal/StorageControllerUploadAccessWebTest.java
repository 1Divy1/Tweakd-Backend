package com.tweakdapp.backend.storage.internal;

import com.tweakdapp.backend.storage.StorageService;
import com.tweakdapp.backend.storage.dto.UploadUrlResponse;
import com.tweakdapp.backend.testsupport.AppWebMvcTest;
import com.tweakdapp.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Resource-scoped upload-URL endpoints must hand the service the JWT subject, since that is who the
 * owning module's policy checks — never an id the caller could choose.
 */
@AppWebMvcTest(StorageController.class)
class StorageControllerUploadAccessWebTest {

    private static final UUID CAR = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID EVENT = UUID.fromString("00000000-0000-0000-0000-0000000000f1");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StorageService storageService;

    @Test
    void unauthenticatedCarUploadRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/storage/cars/{carId}/cover", CAR))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void carCoverIsRequestedOnBehalfOfTheJwtSubject() throws Exception {
        when(storageService.coverUploadUrlRequest(TestJwts.USER_ID, CAR))
                .thenReturn(new UploadUrlResponse("cars/c/cover/x.webp", "http://r2/put"));

        mockMvc.perform(get("/api/storage/cars/{carId}/cover", CAR).with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("cars/c/cover/x.webp"));

        verify(storageService).coverUploadUrlRequest(TestJwts.USER_ID, CAR);
    }

    @Test
    void eventCoverIsRequestedOnBehalfOfTheJwtSubject() throws Exception {
        when(storageService.eventCoverUploadUrlRequest(TestJwts.USER_ID, EVENT))
                .thenReturn(new UploadUrlResponse("events/e/x.webp", "http://r2/put"));

        mockMvc.perform(get("/api/storage/events/{eventId}/cover", EVENT).with(TestJwts.user()))
                .andExpect(status().isOk());

        verify(storageService).eventCoverUploadUrlRequest(TestJwts.USER_ID, EVENT);
    }
}
