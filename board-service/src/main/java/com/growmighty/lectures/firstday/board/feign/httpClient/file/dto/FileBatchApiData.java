package com.growmighty.lectures.firstday.board.feign.httpClient.file.dto;

/** file-service의 GET /internal/v1/files/batch 응답 data 원소 하나. board-service는 ownerId/storedUrl만 필요하다. */
public record FileBatchApiData(Long ownerId, String storedUrl) {
}
