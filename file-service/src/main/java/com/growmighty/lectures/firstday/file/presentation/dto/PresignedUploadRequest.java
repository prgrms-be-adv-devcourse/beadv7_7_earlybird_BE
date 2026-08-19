package com.growmighty.lectures.firstday.file.presentation.dto;

import com.growmighty.lectures.firstday.file.application.dto.PresignedUploadCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PresignedUploadRequest(
        // 허용 목록 밖 contentType(예: text/html)으로 presign 하면 저장된 오브젝트가 그 MIME으로
        // CDN에 서빙되어 저장형 XSS 로 이어질 수 있다 — 썸네일 업로드 용도로 이미지로 제한한다.
        @NotBlank @Pattern(regexp = "^image/(jpeg|png|webp|gif)$") String contentType,
        @NotBlank String originalName
) {
    public PresignedUploadCommand toCommand(Long requesterId) {
        return new PresignedUploadCommand(requesterId, contentType, originalName);
    }
}
