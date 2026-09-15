package com.tweakdapp.backend.storage.internal;

import com.tweakdapp.backend.storage.UploadAccessPolicy;
import com.tweakdapp.backend.storage.UploadTarget;
import com.tweakdapp.backend.storage.dto.UploadUrlResponse;
import com.tweakdapp.backend.storage.internal.cloudflare.PresignedUrlGenerator;
import com.tweakdapp.backend.storage.internal.cloudflare.R2Config;
import com.tweakdapp.backend.storage.internal.enums.FileFormat;
import com.tweakdapp.backend.storage.internal.enums.ModificationPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A presigned URL is a bearer credential for one object key, so {@link StorageServiceImpl} must ask
 * the owning module before minting one — and mint nothing when the answer is no, or when nobody is
 * there to answer.
 */
class StorageServiceImplTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CAR = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID MOD = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID POST = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID EVENT = UUID.fromString("00000000-0000-0000-0000-0000000000f1");

    private PresignedUrlGenerator presigner;
    private R2Config config;

    @BeforeEach
    void setUp() {
        presigner = mock(PresignedUrlGenerator.class);
        when(presigner.generateUploadUrl(any(), any(), any())).thenReturn("http://r2/put");

        config = new R2Config();
        config.setGarage(bucket("garage"));
        config.setPosts(bucket("posts"));
        config.setMapEvents(bucket("map-events"));
    }

    @Test
    void everyResourceScopedRequestAsksItsOwnersPolicyFirst() {
        List<String> checks = new ArrayList<>();
        StorageServiceImpl service = service(
                policy(UploadTarget.CAR, (user, id) -> checks.add("car " + user + " " + id)),
                policy(UploadTarget.POST, (user, id) -> checks.add("post " + user + " " + id)),
                policy(UploadTarget.MAP_EVENT, (user, id) -> checks.add("event " + user + " " + id)));

        service.coverUploadUrlRequest(USER, CAR);
        service.galleryUploadUrlRequest(USER, CAR);
        service.modificationUploadUrlRequest(USER, CAR, MOD, ModificationPhase.BEFORE, FileFormat.WEBP);
        service.modificationBatchUploadUrlRequest(USER, CAR, MOD,
                List.of(new ModificationUploadRequest.MediaItem(ModificationPhase.BEFORE, FileFormat.WEBP)));
        service.postImagesUploadUrlRequest(USER, POST, 2);
        service.eventCoverUploadUrlRequest(USER, EVENT);

        assertThat(checks).containsExactly(
                "car " + USER + " " + CAR,
                "car " + USER + " " + CAR,
                "car " + USER + " " + CAR,
                "car " + USER + " " + CAR,
                "post " + USER + " " + POST,
                "event " + USER + " " + EVENT);
    }

    @Test
    void aDeniedCallerIsNeverHandedAUrl() {
        BiConsumer<UUID, UUID> deny = (user, id) -> {
            throw new IllegalArgumentException("denied");
        };
        StorageServiceImpl service = service(
                policy(UploadTarget.CAR, deny),
                policy(UploadTarget.POST, deny),
                policy(UploadTarget.MAP_EVENT, deny));

        assertThatThrownBy(() -> service.coverUploadUrlRequest(USER, CAR)).hasMessage("denied");
        assertThatThrownBy(() -> service.galleryUploadUrlRequest(USER, CAR)).hasMessage("denied");
        assertThatThrownBy(() -> service.modificationUploadUrlRequest(
                USER, CAR, MOD, ModificationPhase.AFTER, FileFormat.WEBP)).hasMessage("denied");
        assertThatThrownBy(() -> service.modificationBatchUploadUrlRequest(USER, CAR, MOD,
                List.of(new ModificationUploadRequest.MediaItem(ModificationPhase.AFTER, FileFormat.WEBP))))
                .hasMessage("denied");
        assertThatThrownBy(() -> service.postImagesUploadUrlRequest(USER, POST, 1)).hasMessage("denied");
        assertThatThrownBy(() -> service.eventCoverUploadUrlRequest(USER, EVENT)).hasMessage("denied");

        verifyNoInteractions(presigner);
    }

    @Test
    void aTargetWithoutAPolicyFailsClosed() {
        StorageServiceImpl service = service(policy(UploadTarget.CAR, (user, id) -> { }));

        assertThatThrownBy(() -> service.postImagesUploadUrlRequest(USER, POST, 1))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.eventCoverUploadUrlRequest(USER, EVENT))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(presigner);
    }

    /** A fixed cover key would let any URL minted for the car overwrite the cover that is live. */
    @Test
    void everyCoverUploadGetsAFreshKeyUnderTheCar() {
        StorageServiceImpl service = service(policy(UploadTarget.CAR, (user, id) -> { }));

        UploadUrlResponse first = service.coverUploadUrlRequest(USER, CAR);
        UploadUrlResponse second = service.coverUploadUrlRequest(USER, CAR);

        assertThat(first.key()).startsWith("cars/" + CAR + "/cover/").endsWith(".webp");
        assertThat(second.key()).startsWith("cars/" + CAR + "/cover/").isNotEqualTo(first.key());
    }

    private StorageServiceImpl service(UploadAccessPolicy... policies) {
        return new StorageServiceImpl(presigner, config, mock(S3Client.class), List.of(policies));
    }

    private static UploadAccessPolicy policy(UploadTarget target, BiConsumer<UUID, UUID> check) {
        return new UploadAccessPolicy() {
            @Override
            public UploadTarget target() {
                return target;
            }

            @Override
            public void requireUploadAccess(UUID userId, UUID resourceId) {
                check.accept(userId, resourceId);
            }
        };
    }

    private static R2Config.BucketTarget bucket(String name) {
        R2Config.BucketTarget target = new R2Config.BucketTarget();
        target.setBucket(name);
        target.setPublicUrl("https://" + name + ".example");
        return target;
    }
}
