package com.growmighty.lectures.firstday.file.application.dto;

import com.growmighty.lectures.firstday.file.domain.FileOwnerType;

public record RegisterFileCommand(
        FileOwnerType ownerType,
        Long ownerId,
        Long uploaderId,
        String storedUrl,
        String originalName,
        String contentType,
        Long fileSize,
        int sortOrder
) {
}
