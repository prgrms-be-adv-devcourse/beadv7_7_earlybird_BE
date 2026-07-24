package com.growmighty.lectures.firstday.order.infrastructure.client;

import com.growmighty.lectures.firstday.order.application.port.PaymentPort;
import com.growmighty.lectures.firstday.order.application.port.dto.PaymentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
//import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
public class PaymentHttpClient implements PaymentPort {

    // TODO(예정) : 서킷브레이커 추가

    @Override
    public PaymentResult pay(UUID orderId, Long userId, BigDecimal amount) {
        // TODO(예정, 상세 미정) : 검증 추가
        log.info("payment request stub succeeded. orderId={}", orderId);
        return PaymentResult.success(1L, amount);
    }

    @Override
    public RefundResult refund(UUID orderId, BigDecimal amount) {
        // TODO(예정) : payment 연동
        log.info("payment refund stub succeeded. orderId={}", orderId);
        return RefundResult.success(amount, "stub-refund-" + orderId);
    }

    @Override
    public PaymentResult getPaymentResult(UUID orderId) {
        // TODO(예정) : payment 연동
        log.info("payment result stub succeeded. orderId={}", orderId);
        return PaymentResult.success(1L, BigDecimal.ZERO);
    }
}
