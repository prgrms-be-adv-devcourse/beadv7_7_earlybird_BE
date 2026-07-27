package com.growmighty.lectures.firstday.board.infrastructure.client.order;

import com.growmighty.lectures.firstday.board.application.port.OrderPort;
import com.growmighty.lectures.firstday.board.application.port.dto.PurchaseVerification;
import com.growmighty.lectures.firstday.board.infrastructure.client.order.dto.OrderPurchaseVerificationApiData;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderHttpClient implements OrderPort {

    private final OrderFeignClient orderFeignClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    // 주의: 여기서 하드 실패시키는 건 "order-service 자체와 통신이 안 될 때"뿐이다.
    // order-service가 정상 응답했지만 verified=false인 건 정당한 판정 결과이지 장애가 아니므로,
    // 그대로 호출자(ReviewService)에게 돌려주고 거기서 비즈니스 예외로 처리한다.
    @Override
    public PurchaseVerification verifyPurchase(Long userId, Long rewardId) {
        return circuitBreakerFactory.create("order").run(
            () -> fetch(userId, rewardId),
            cause -> failHard(userId, rewardId, cause));
    }

    private PurchaseVerification fetch(Long userId, Long rewardId) {
        OrderPurchaseVerificationApiData data = orderFeignClient.verifyPurchase(userId, rewardId).data();
        return new PurchaseVerification(data.verified(), data.rewardName());
    }

    private PurchaseVerification failHard(Long userId, Long rewardId, Throwable cause) {
        log.warn("구매 검증 조회 실패. userId={}, rewardId={}, 원인={}", userId, rewardId, cause.toString());
        throw new ServiceUnavailableException(
            "구매 정보를 확인할 수 없어 요청을 처리할 수 없습니다. userId=" + userId + ", rewardId=" + rewardId);
    }
}