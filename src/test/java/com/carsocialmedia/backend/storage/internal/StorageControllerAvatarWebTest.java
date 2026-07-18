package com.carsocialmedia.backend.storage.internal;

import com.carsocialmedia.backend.storage.StorageService;
import com.carsocialmedia.backend.storage.dto.UploadUrlResponse;
import com.carsocialmedia.backend.testsupport.AppWebMvcTest;
import com.carsocialmedia.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The avatar upload-URL endpoint on {@link StorageController}: auth requirement and
 * JWT-subject-scoped delegation returning the {@code {key, upload_url}} slot.
 */
@AppWebMvcTest(StorageController.class)
class StorageControllerAvatarWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StorageService storageService;

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/storage/avatar"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void avatarReturnsPresignedUrlNamespacedByTheJwtSubject() throws Exception {
        when(storageService.avatarUploadUrlRequest(TestJwts.USER_ID))
                .thenReturn(new UploadUrlResponse("avatars/u/pic.webp", "http://r2/put"));

        mockMvc.perform(get("/api/storage/avatar").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("avatars/u/pic.webp"))
                .andExpect(jsonPath("$.upload_url").value("http://r2/put"));

        verify(storageService).avatarUploadUrlRequest(TestJwts.USER_ID);
    }
}
