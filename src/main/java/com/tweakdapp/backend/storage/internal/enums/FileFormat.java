package com.tweakdapp.backend.storage.internal.enums;

public enum FileFormat {
    WEBP(".webp", "image/webp"),
    MP4(".mp4", "video/mp4");

    private final String extension;
    private final String contentType;

    FileFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String getExtension()   {
        return extension;
    }

    public String getContentType() {
        return contentType;
    }
}
