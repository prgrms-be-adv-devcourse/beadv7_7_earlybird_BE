package com.growmighty.lectures.firstday.file.presentation.dto;

import com.growmighty.lectures.firstday.file.application.dto.RegisterFileCommand;
import com.growmighty.lectures.firstday.file.domain.FileOwnerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RegisterFileRequest(
        @NotNull FileOwnerType ownerType,
        @NotNull Long ownerId,
        @NotBlank String storedUrl,
        @NotBlank String originalName,
        @NotBlank String contentType,
        @NotNull @Positive Long fileSize,
        int sortOrder
) {
    public RegisterFileCommand toCommand(Long requesterId) {
        return new RegisterFileCommand(ownerType, ownerId, requesterId, storedUrl, originalName, contentType, fileSize, sortOrder);
    }
}
