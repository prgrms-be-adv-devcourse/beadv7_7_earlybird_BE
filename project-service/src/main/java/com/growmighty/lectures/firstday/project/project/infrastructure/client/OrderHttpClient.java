package com.growmighty.lectures.firstday.project.project.infrastructure.client;

import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.project.project.application.port.OrderPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderHttpClient implements OrderPort {

    private final OrderFeignClient orderFeignClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Override
    public boolean hasOrderedReward(Long projectId) {
        return circuitBreakerFactory.create("order").run(
            () -> orderFeignClient.hasOrderedReward(projectId).data(),
            this::hasOrderedRewardFallback);
    }

    private boolean hasOrderedRewardFallback(Throwable cause) {
        log.warn("주문 존재 여부 확인 호출 실패 → fallback 실행. 원인: {}", cause.toString());
        // "확인 안 됨"을 "후원 없음"으로 잘못 판단해 삭제를 허용하면 안 된다 —
        // 결제와 같은 이유로, 실패는 정직하게 503으로 알리고 삭제는 막는다(fail-closed).
        throw new ServiceUnavailableException(
            "주문 서비스가 일시적으로 응답하지 않아 삭제 가능 여부를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    }

    @Override
    public BigDecimal getFundedAmount(Long projectId) {
        return circuitBreakerFactory.create("order").run(
            () -> resolveFundedAmount(orderFeignClient.getFundedAmount(projectId).data()),
            this::getFundedAmountFallback);
    }

    /** 아직 후원이 없는 프로젝트는 order-service가 data=null을 준다 — 0원으로 취급한다. */
    private BigDecimal resolveFundedAmount(BigDecimal fundedAmount) {
        return fundedAmount != null ? fundedAmount : BigDecimal.ZERO;
    }

    private BigDecimal getFundedAmountFallback(Throwable cause) {
        log.warn("모금액 확정 총액 조회 실패 → fallback 실행. 원인: {}", cause.toString());
        // 이 호출은 project.fundedAmount를 덮어쓸 값을 가져오는 pull이라, 실패했다고 0 등으로
        // 잘못 덮어쓰면 안 된다 — 호출자(ProjectServiceImpl.reconcileFundedAmounts)가 이번
        // 프로젝트만 건너뛰도록 정직하게 503으로 알린다.
        throw new ServiceUnavailableException(
            "주문 서비스가 일시적으로 응답하지 않아 모금액을 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    }
}
