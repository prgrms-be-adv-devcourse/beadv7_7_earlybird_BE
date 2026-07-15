package com.growmighty.lectures.firstday.file.presentation.dto;

import com.growmighty.lectures.firstday.file.application.dto.FileInfo;

public record FileResponse(
        Long id,
        String ownerType,
        Long ownerId,
        String storedUrl,
        String originalName,
        String contentType,
        Long fileSize,
        int sortOrder
) {
    public static FileResponse from(FileInfo info) {
        return new FileResponse(
                info.id(),
                info.ownerType().name(),
                info.ownerId(),
                info.storedUrl(),
                info.originalName(),
                info.contentType(),
                info.fileSize(),
                info.sortOrder());
    }
}
