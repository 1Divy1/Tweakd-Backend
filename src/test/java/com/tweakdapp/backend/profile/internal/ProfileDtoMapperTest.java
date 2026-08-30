package com.tweakdapp.backend.profile.internal;

import com.tweakdapp.backend.profile.dto.ProfileDto;
import com.tweakdapp.backend.shared.geo.CityEntity;
import com.tweakdapp.backend.profile.internal.entity.ProfileEntity;
import com.tweakdapp.backend.storage.StorageBucket;
import com.tweakdapp.backend.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The central avatar-URL resolution rule shared by every profile DTO path: blank passthrough,
 * legacy {@code http} passthrough, and R2-key → public URL via {@link StorageService}.
 */
class ProfileDtoMapperTest {

    private StorageService storageService;
    private ProfileDtoMapper mapper;

    @BeforeEach
    void setUp() {
        storageService = mock(StorageService.class);
        mapper = new ProfileDtoMapper(storageService);
    }

    @Test
    void resolveAvatarUrlPassesNullThroughUnchangedWithoutTouchingStorage() {
        assertThat(mapper.resolveAvatarUrl(null)).isNull();
        verifyNoInteractions(storageService);
    }

    @Test
    void resolveAvatarUrlPassesBlankThroughUnchangedWithoutTouchingStorage() {
        assertThat(mapper.resolveAvatarUrl("")).isEmpty();
        verifyNoInteractions(storageService);
    }

    @Test
    void resolveAvatarUrlPassesLegacyHttpUrlThroughUnchangedWithoutTouchingStorage() {
        String google = "https://lh3.googleusercontent.com/a/photo.jpg";
        assertThat(mapper.resolveAvatarUrl(google)).isEqualTo(google);
        verifyNoInteractions(storageService);
    }

    @Test
    void resolveAvatarUrlBuildsPublicUrlFromAnR2Key() {
        when(storageService.publicUrl(eq(StorageBucket.AVATARS), eq("avatars/u/pic.webp")))
                .thenReturn("http://cdn/avatars/u/pic.webp");

        assertThat(mapper.resolveAvatarUrl("avatars/u/pic.webp"))
                .isEqualTo("http://cdn/avatars/u/pic.webp");
    }

    @Test
    void toDtoResolvesTheAvatarKeyAndCarriesTheLanguage() {
        when(storageService.publicUrl(eq(StorageBucket.AVATARS), eq("avatars/u/pic.webp")))
                .thenReturn("http://cdn/avatars/u/pic.webp");

        CityEntity city = new CityEntity();
        city.setId("cluj");
        ProfileEntity p = new ProfileEntity();
        p.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        p.setUsername("racer");
        p.setAvatarUrl("avatars/u/pic.webp");
        p.setAppLanguage("ro");
        p.setCity(city);

        ProfileDto dto = mapper.toDto(p);

        assertThat(dto.avatarUrl()).isEqualTo("http://cdn/avatars/u/pic.webp");
        assertThat(dto.appLanguage()).isEqualTo("ro");
        assertThat(dto.cityId()).isEqualTo("cluj");
    }
}
