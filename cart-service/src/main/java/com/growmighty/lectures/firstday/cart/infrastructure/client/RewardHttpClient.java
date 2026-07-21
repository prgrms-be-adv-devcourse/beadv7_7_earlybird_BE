package com.growmighty.lectures.firstday.cart.infrastructure.client;

import com.growmighty.lectures.firstday.cart.application.port.RewardPort;
import com.growmighty.lectures.firstday.cart.application.port.dto.RewardSnapshot;
import com.growmighty.lectures.firstday.cart.infrastructure.client.dto.ApiResponseBody;
import com.growmighty.lectures.firstday.cart.infrastructure.client.dto.ProjectApiData;
import com.growmighty.lectures.firstday.cart.infrastructure.client.dto.RewardApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RewardHttpClient implements RewardPort {

    private final RestClient projectRestClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Override
    public RewardSnapshot getReward(Long rewardId) {
        return circuitBreakerFactory.create("reward").run(
            () -> callGetReward(rewardId, new HashMap<>()),
            cause -> optimisticFallback(rewardId, cause));
    }

    @Override
    public Map<Long, RewardSnapshot> getRewards(Collection<Long> rewardIds) {
        return circuitBreakerFactory.create("reward").run(
            () -> {
                Map<Long, ProjectApiData> projects = new HashMap<>();
                Map<Long, RewardSnapshot> rewards = new HashMap<>();
                rewardIds.stream().distinct()
                        .forEach(rewardId -> rewards.put(rewardId, callGetReward(rewardId, projects)));
                return rewards;
            },
            cause -> {
                log.warn("리워드 배치 확인 실패 후 낙관적으로 담기 진행. rewardIds={}, 원인={}", rewardIds, cause.toString());
                Map<Long, RewardSnapshot> rewards = new HashMap<>();
                rewardIds.stream().distinct()
                        .forEach(rewardId -> rewards.put(rewardId, new RewardSnapshot(rewardId, true)));
                return rewards;
            });
    }

    private RewardSnapshot callGetReward(Long rewardId, Map<Long, ProjectApiData> projects) {
        ApiResponseBody<RewardApiData> body = projectRestClient.get()
            .uri("/api/v1/rewards/{rewardId}", rewardId)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });

        RewardApiData data = body.data();
        ProjectApiData project = data.projectId() == null
                ? null
                : projects.computeIfAbsent(data.projectId(), this::callGetProject);
        return new RewardSnapshot(
                data.id(),
                data.projectId(),
                data.name(),
                project == null ? null : project.title(),
                data.price(),
                data.remainingQuantity(),
                data.orderable() && (project == null || project.orderable()));
    }

    private ProjectApiData callGetProject(Long projectId) {
        ApiResponseBody<ProjectApiData> body = projectRestClient.get()
            .uri("/api/v1/projects/{projectId}", projectId)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });

        return body.data();
    }

    private RewardSnapshot optimisticFallback(Long rewardId, Throwable cause) {
        log.warn("리워드 확인 실패 → 낙관적으로 담기 진행. rewardId={}, 원인={}", rewardId, cause.toString());
        // 장바구니 담기는 구매가 아니다 — 최종 검증(판매 가능/재고)은 어차피 주문 시점에 다시 한다.
        // 그래서 확인이 안 되면 '판매 중이겠거니' 하고 일단 담아 준다.
        // 사용자는 project-service 가 죽었다는 사실조차 모른다. 이것이 우아한 실패(graceful degradation)다.
        return new RewardSnapshot(rewardId, true);
    }
}
