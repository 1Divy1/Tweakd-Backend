package com.carsocialmedia.backend.storage.internal;

import com.carsocialmedia.backend.storage.StorageService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class StorageServiceImpl implements StorageService {

    private final SupabaseStorageService storageClient;

    StorageServiceImpl(SupabaseStorageService storageClient) {
        this.storageClient = storageClient;
    }

    @Override
    public String createUploadUrl(String path) {
        return storageClient.createSignedUploadUrl(path);
    }

    @Override
    public String createDownloadUrl(String path) {
        return storageClient.createSignedDownloadUrl(path);
    }

    @Override
    public void deleteObjects(List<String> paths) {
        storageClient.deleteObjects(paths);
    }
}
