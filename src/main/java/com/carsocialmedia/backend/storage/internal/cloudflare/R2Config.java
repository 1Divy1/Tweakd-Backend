package com.carsocialmedia.backend.storage.internal.cloudflare;

import com.carsocialmedia.backend.storage.StorageBucket;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cloudflare.r2")
@Getter
@Setter
public class R2Config {
    private String accountId;
    private String accessKey;
    private String secretKey;
    private int presignExpiryMinutes;

    private BucketTarget garage;
    private BucketTarget posts;
    private BucketTarget avatars;

    /**
     * Resolves a logical bucket to its configured name + public URL. Add a case (and a config
     * entry) here when a new {@link StorageBucket} constant is introduced.
     */
    public BucketTarget target(StorageBucket bucket) {
        return switch (bucket) {
            case GARAGE -> garage;
            case POSTS -> posts;
            case AVATARS -> avatars;
        };
    }

    /*
        Groups bucket + public URL
    */
    @Getter
    @Setter
    public static class BucketTarget {
        private String bucket;
        private String publicUrl;
    }
}
