package com.growmighty.lectures.firstday.ai.tool.feign.httpClient.project;

import com.growmighty.lectures.firstday.ai.tool.feign.httpClient.project.dto.RewardApiData;
import com.growmighty.lectures.firstday.ai.tool.feign.port.project.RewardPort;
import com.growmighty.lectures.firstday.ai.tool.feign.port.project.dto.RewardResult;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RewardHttpClient implements RewardPort {

    private final RewardFeignClient rewardFeignClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Override
    public List<RewardResult> findByProject(Long projectId) {
        return circuitBreakerFactory.create("rewards").run(
            () -> fetchByProject(projectId),
            cause -> failHardList(projectId, cause)
        );
    }

    private List<RewardResult> fetchByProject(Long projectId) {
        List<RewardApiData> data = rewardFeignClient.getRewardByProject(projectId).data();
        return data.stream().map(this::toResult).toList();
    }

    private RewardResult toResult(RewardApiData data) {
        return new RewardResult(
            data.rewardId(),
            data.projectId(),
            data.name(),
            data.description(),
            data.price(),
            data.totalQuantity(),
            data.remainingQuantity(),
            data.orderable(),
            data.active()
        );
    }

    private List<RewardResult> failHardList(Long projectId, Throwable cause) {
        log.warn("프로젝트 리워드 목록 조회 실패. projectId={}, 원인 ={}", projectId, cause.toString());
        throw new ServiceUnavailableException("리워드 목록을 조회할 수 없습니다. projectId=" + projectId);
    }

    @Override
    public RewardResult findById(Long rewardId) {
        return circuitBreakerFactory.create("rewards").run(
            () -> fetchById(rewardId),
            cause -> failHardSingle(rewardId, cause)
        );
    }

    private RewardResult fetchById(Long rewardId) {
        return toResult(rewardFeignClient.getReward(rewardId).data());
    }

    private RewardResult failHardSingle(Long rewardId, Throwable cause) {
        log.warn("리워드 상세 조회 실패. rewardId={}, 원인 ={}",rewardId, cause.toString());
        throw new ServiceUnavailableException("리워드 상세 정보를 조회할 수 없습니다. rewardId=" + rewardId);
    }
}
