package com.growmighty.lectures.firstday.order.infrastructure.client;

import com.growmighty.lectures.firstday.order.application.port.RewardPort;
import com.growmighty.lectures.firstday.order.application.port.dto.RewardSnapshot;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.ApiResponseBody;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.RewardApiData;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.StockChangeBody;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// Feign 프록시가 곧 RewardPort 구현체다 — 별도 어댑터 없이 포트를 직접 구현한다.
// name = Eureka 전화번호부에 올라간 이름 — 리워드는 project-service 안의 도메인이다. URL 이 아니다!
// 호출 시점마다 내장 로드밸런서가 명부에서 실제 주소를 찾아 치환한다.
@FeignClient(name = "project-service")
public interface RewardFeignClient extends RewardPort {

    // ── 실제 HTTP 계약 (Feign 이 프록시로 구현) ──────────────────────────
    // project-service 의 RewardController 시그니처를 거울처럼 비춰 적는다.
    // 응답 봉투(ApiResponseBody)와 API DTO(RewardApiData)는 HTTP 세계의 언어라 여기 남긴다.
    @GetMapping("/rewards/{rewardId}")
    ApiResponseBody<RewardApiData> fetchReward(@PathVariable("rewardId") Long rewardId);

    @PostMapping("/internal/rewards/{rewardId}/decrease-stock")
    void sendDecreaseStock(@PathVariable("rewardId") Long rewardId, @RequestBody StockChangeBody body);

    @PostMapping("/internal/rewards/{rewardId}/restore-stock")
    void sendRestoreStock(@PathVariable("rewardId") Long rewardId, @RequestBody StockChangeBody body);

    // ── 포트 구현 (default 메서드 = 순수 자바, HTTP 프록시가 아니다) ──────
    // 봉투 벗기기 + "API DTO → 도메인 언어" 번역(ACL)을 여기서 한다.
    // 이 번역 계층 덕에 리워드의 응답 형태가 바뀌어도 여파가 이 파일에서 멈춘다.
    @Override
    default RewardSnapshot getReward(Long rewardId) {
        RewardApiData data = fetchReward(rewardId).data();
        return new RewardSnapshot(
                data.id(),
                data.projectId(),
                data.name(),
                data.price(),
                data.remainingQuantity(),
                data.orderable());
    }

    @Override
    default void decreaseStock(Long rewardId, int quantity) {
        sendDecreaseStock(rewardId, new StockChangeBody(quantity));
    }

    @Override
    default void restoreStock(Long rewardId, int quantity) {
        sendRestoreStock(rewardId, new StockChangeBody(quantity));
    }
}
