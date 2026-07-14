package com.growmighty.lectures.firstday.cart.infrastructure.client;

import com.growmighty.lectures.firstday.cart.application.port.ProjectPort;
import com.growmighty.lectures.firstday.cart.application.port.dto.ProjectSnapshot;
import com.growmighty.lectures.firstday.cart.infrastructure.client.dto.ApiResponseBody;
import com.growmighty.lectures.firstday.cart.infrastructure.client.dto.ProjectApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectHttpClient implements ProjectPort {

    private final RestClient projectRestClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Override
    public ProjectSnapshot getProject(Long projectId) {
        return circuitBreakerFactory.create("project").run(
            () -> callGetProject(projectId),
            cause -> optimisticFallback(projectId, cause));
    }

    private ProjectSnapshot callGetProject(Long projectId) {
        ApiResponseBody<ProjectApiData> body = projectRestClient.get()
            .uri("/projects/{projectId}", projectId)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });

        ProjectApiData data = body.data();
        return new ProjectSnapshot(data.id(), data.orderable());
    }

    private ProjectSnapshot optimisticFallback(Long projectId, Throwable cause) {
        log.warn("프로젝트 확인 실패 → 낙관적으로 담기 진행. projectId={}, 원인={}", projectId, cause.toString());
        // 장바구니 담기는 구매가 아니다 — 최종 검증(판매 가능/재고)은 어차피 주문 시점에 다시 한다.
        // 그래서 확인이 안 되면 '판매 중이겠거니' 하고 일단 담아 준다.
        // 사용자는 project-service 가 죽었다는 사실조차 모른다. 이것이 우아한 실패(graceful degradation)다.
        return new ProjectSnapshot(projectId, true);
    }
}
