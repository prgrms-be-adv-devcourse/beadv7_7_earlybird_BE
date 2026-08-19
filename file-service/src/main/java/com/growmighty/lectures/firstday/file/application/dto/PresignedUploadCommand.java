package com.growmighty.lectures.firstday.file.application.dto;

public record PresignedUploadCommand(Long requesterId, String contentType, String originalName) {
}
