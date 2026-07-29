package com.growmighty.lectures.firstday.board.feign.httpClient.project.dto;

/** project-service의 GET /internal/v1/projects/{projectId}/creator 응답 data 부분. */
public record ProjectCreatorApiData(Long creatorId) {
}