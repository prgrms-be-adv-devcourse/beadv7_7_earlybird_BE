package com.growmighty.lectures.firstday.file.presentation.dto;

import com.growmighty.lectures.firstday.file.application.dto.RegisterFileCommand;
import com.growmighty.lectures.firstday.file.domain.FileOwnerType;
import lombok.NonNull;

public record RegisterFileRequest(
        @NonNull FileOwnerType ownerType,
        @NonNull Long ownerId,
        @NonNull String storedUrl,
        @NonNull String originalName,
        @NonNull String contentType,
        @NonNull Long fileSize,
        int sortOrder
) {
    public RegisterFileCommand toCommand(Long requesterId) {
        return new RegisterFileCommand(ownerType, ownerId, requesterId, storedUrl, originalName, contentType, fileSize, sortOrder);
    }
}
