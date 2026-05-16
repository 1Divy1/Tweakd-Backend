package com.carsocialmedia.backend.storage.internal;

import com.carsocialmedia.backend.storage.internal.exceptions.StorageServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Talks to the Supabase Storage REST API to mint presigned upload/download URLs and
 * delete objects. Authenticates with the service-role key; no file bytes pass through here.
 */
@Service
class SupabaseStorageService {

    private final RestClient restClient;
    private final StorageProperties properties;
    private final String storageBaseUrl;

    SupabaseStorageService(@Value("${supabase.url}") String supabaseUrl,
                           StorageProperties properties) {
        this.properties = properties;
        this.storageBaseUrl = supabaseUrl + "/storage/v1";
        this.restClient = RestClient.builder()
                .baseUrl(this.storageBaseUrl)
                .defaultHeader("Authorization", "Bearer " + properties.getServiceRoleKey())
                .defaultHeader("apikey", properties.getServiceRoleKey())
                .build();
    }

    /**
     * Mints a presigned URL the client PUTs the file bytes to directly.
     *
     * @param path object path inside the bucket (no leading slash)
     * @return an absolute, single-use Supabase upload URL
     */
    String createSignedUploadUrl(String path) {
        URI uri = URI.create(storageBaseUrl + "/object/upload/sign/" + properties.getBucket() + "/" + path);
        try {
            Map<?, ?> body = restClient.post()
                    .uri(uri)
                    .retrieve()
                    .body(Map.class);
            String relative = body == null ? null : (String) body.get("url");
            if (relative == null) {
                throw new StorageServiceException("No upload URL returned for path: " + path);
            }
            return storageBaseUrl + relative;
        } catch (RestClientException e) {
            throw new StorageServiceException("Failed to create upload URL for path: " + path);
        }
    }

    /**
     * Mints a short-lived presigned URL for reading an object.
     *
     * @param path object path inside the bucket (no leading slash)
     * @return an absolute, time-limited Supabase download URL
     */
    String createSignedDownloadUrl(String path) {
        URI uri = URI.create(storageBaseUrl + "/object/sign/" + properties.getBucket() + "/" + path);
        try {
            Map<?, ?> body = restClient.post()
                    .uri(uri)
                    .body(Map.of("expiresIn", properties.getSignedUrlTtl()))
                    .retrieve()
                    .body(Map.class);
            String relative = body == null ? null : (String) body.get("signedURL");
            if (relative == null) {
                throw new StorageServiceException("No download URL returned for path: " + path);
            }
            return storageBaseUrl + relative;
        } catch (RestClientException e) {
            throw new StorageServiceException("Failed to create download URL for path: " + path);
        }
    }

    /**
     * Best-effort bulk delete. Callers treat DB rows as the source of truth, so a failure
     * here only leaves orphaned objects — it must not fail the caller's operation.
     *
     * @param paths object paths inside the bucket (no leading slash)
     */
    void deleteObjects(List<String> paths) {
        if (paths.isEmpty()) {
            return;
        }
        URI uri = URI.create(storageBaseUrl + "/object/" + properties.getBucket());
        try {
            restClient.method(HttpMethod.DELETE)
                    .uri(uri)
                    .body(Map.of("prefixes", paths))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new StorageServiceException("Failed to delete objects: " + paths);
        }
    }
}
