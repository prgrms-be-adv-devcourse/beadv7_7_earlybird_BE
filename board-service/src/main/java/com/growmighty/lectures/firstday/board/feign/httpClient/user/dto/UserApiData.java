package com.growmighty.lectures.firstday.board.feign.httpClient.user.dto;

/**
 * user-service 의 GET /internal/v1/users/{userId} 응답 data 부분.
 * 여기서 실제로 쓰는 값은 id/name 뿐이다 (모르는 JSON 필드는 무시되므로 전체 응답을 다 적을 필요는 없다).
 */
public record UserApiData(Long id, String name) {
}