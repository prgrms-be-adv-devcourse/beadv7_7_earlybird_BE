package com.growmighty.lectures.firstday.file.infrastructure.client;

import com.growmighty.lectures.firstday.common.exception.BusinessException;
import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.file.application.port.ProjectPort;
import com.growmighty.lectures.firstday.file.infrastructure.client.dto.ProjectCreatorApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectHttpClient implements ProjectPort {

    private final RestClient projectRestClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Override
    public Long getCreatorId(Long projectId) {
        return circuitBreakerFactory.create("project-creator").run(
            () -> callGetCreator(projectId),
            cause -> failClosed(projectId, cause));
    }

    private Long callGetCreator(Long projectId) {
        // /internal/v1/** 는 게이트웨이 라우트가 없다 — Eureka-to-Eureka 직접 호출 전용.
        ApiResponse<ProjectCreatorApiData> body = projectRestClient.get()
            .uri("/internal/v1/projects/{projectId}/creator", projectId)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });
        return body.data().creatorId();
    }

    // 소유권 확인은 보안 경계다 — cart의 리워드 조회처럼 "확인 안 되면 낙관적으로 통과"시키면
    // project-service 장애 상황에서 소유권 검증이 통째로 무력화된다. 확인 불가 시 거부(fail-closed)한다.
    private Long failClosed(Long projectId, Throwable cause) {
        log.warn("프로젝트 소유자 확인 실패 - 소유권 검증 필요 요청을 거부한다. projectId={}, 원인={}", projectId, cause.toString());
        throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
            "프로젝트 소유권을 확인할 수 없어 요청을 처리할 수 없습니다. 잠시 후 다시 시도해주세요.");
    }
}
