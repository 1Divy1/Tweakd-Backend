package com.carsocialmedia.backend.storage.internal.cloudflare;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class PresignedUrlGenerator {

    private final S3Presigner presigner;
    private final R2Config config;

    public String generateUploadUrl(String bucket, String key, String contentType) {
        PutObjectPresignRequest request = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(config.getPresignExpiryMinutes()))
                .putObjectRequest(r -> r
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                )
                .build();

        return presigner.presignPutObject(request).url().toString();
    }
}
