package com.growmighty.lectures.firstday.ai.tool.feign.httpClient.file.dto;

// file-service의 GET /api/v1/files 응답(FileResponse) 중 썸네일 URL 조회에 필요한 필드만 뽑은 DTO
public record FileApiData(
    Long id,
    String storedUrl
) {
}
