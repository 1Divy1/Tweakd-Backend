package com.carsocialmedia.backend.storage.internal;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("supabase.storage")
@Getter
@Setter
class StorageProperties {
    private String bucket;
    private int signedUrlTtl;
    private String serviceRoleKey;
}
