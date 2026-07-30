package com.growmighty.lectures.firstday.order.infrastructure.client;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.order.application.port.PaymentPort;
import com.growmighty.lectures.firstday.order.application.port.dto.PaymentResult;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.PayBody;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.PaymentApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
//import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentHttpClient implements PaymentPort {

    // TODO(예정) : 서킷브레이커 추가
    private final PaymentFeignClient paymentFeignClient;

    @Override
    public PaymentResult pay(Long orderId, Long userId, BigDecimal amount) {
        // TODO(예정, 상세 미정) : 검증 추가?
        try {
            ApiResponse<PaymentApiData> response = paymentFeignClient.pay(new PayBody(orderId, amount));
            return toPaymentResult(response, amount);
        } catch (RuntimeException e) {
            log.warn("payment prepare request failed. orderId={}", orderId, e);
            return PaymentResult.unknown(amount);
        }
    }

    @Override
    public RefundResult refund(Long orderId, BigDecimal amount) {
        // TODO(예정) : payment 연동
        log.info("payment refund stub succeeded. orderId={}", orderId);
        return RefundResult.success(amount, "stub-refund-" + orderId);
    }

    @Override
    public PaymentResult getPaymentResult(Long orderId) {
        // TODO(예정) : payment 연동
        log.info("payment result stub succeeded. orderId={}", orderId);
        return PaymentResult.success(1L, BigDecimal.ZERO);
    }

    private PaymentResult toPaymentResult(ApiResponse<PaymentApiData> response, BigDecimal requestedAmount) {
        if (response == null || !response.success() || response.data() == null || response.data().status() == null) {
            return PaymentResult.unknown(requestedAmount);
        }

        PaymentApiData data = response.data();
        BigDecimal amount = data.amount() != null ? data.amount() : requestedAmount;
        return switch (data.status()) {
            case "PAID" -> PaymentResult.success(data.paymentId(), amount);
            case "FAILED", "CANCELLED" -> PaymentResult.failure(amount);
            case "READY", "CONFIRMING" -> PaymentResult.pending(amount);
            default -> PaymentResult.unknown(amount);
        };
    }
}
