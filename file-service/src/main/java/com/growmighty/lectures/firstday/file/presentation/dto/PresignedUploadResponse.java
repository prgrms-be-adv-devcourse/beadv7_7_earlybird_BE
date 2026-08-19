package com.growmighty.lectures.firstday.file.presentation.dto;

import com.growmighty.lectures.firstday.file.application.dto.PresignedUploadInfo;

import java.util.Map;

public record PresignedUploadResponse(String uploadUrl, String storedUrl, Map<String, String> requiredHeaders) {
    public static PresignedUploadResponse from(PresignedUploadInfo info) {
        return new PresignedUploadResponse(info.uploadUrl(), info.storedUrl(), info.requiredHeaders());
    }
}
