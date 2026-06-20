package com.carsocialmedia.backend.storage.internal.cloudflare;

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
