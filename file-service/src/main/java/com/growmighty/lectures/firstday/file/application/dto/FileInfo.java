package com.growmighty.lectures.firstday.file.application.dto;

import com.growmighty.lectures.firstday.file.domain.File;
import com.growmighty.lectures.firstday.file.domain.FileOwnerType;

public record FileInfo(
        Long id,
        FileOwnerType ownerType,
        Long ownerId,
        String storedUrl,
        String originalName,
        String contentType,
        Long fileSize,
        int sortOrder
) {
    public static FileInfo from(File file, String downloadUrl) {
        return new FileInfo(
                file.getId(),
                file.getOwnerType(),
                file.getOwnerId(),
                downloadUrl,
                file.getOriginalName(),
                file.getContentType(),
                file.getFileSize(),
                file.getSortOrder());
    }
}
