package com.growmighty.lectures.firstday.file.application.dto;

import java.util.Map;

public record PresignedUploadInfo(String uploadUrl, String storedUrl, Map<String, String> requiredHeaders) {
}
