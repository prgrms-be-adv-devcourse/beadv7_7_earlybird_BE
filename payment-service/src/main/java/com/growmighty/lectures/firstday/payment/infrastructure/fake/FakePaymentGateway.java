package com.growmighty.lectures.firstday.payment.infrastructure.fake;

import com.growmighty.lectures.firstday.payment.application.PaymentGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

// Step 4의 배너 데모(@RefreshScope + @Value)와 같은 패턴.
// config-repo 값을 바꾸고 POST /actuator/refresh 하면 "재시작 없이" 지연을 켜고 끈다.
@RefreshScope
@Component
@ConditionalOnProperty(
    name = "payment.gateway",
    havingValue = "fake",
    matchIfMissing = true
)
public class FakePaymentGateway implements PaymentGateway {
    // 실습용 "PG사 점검 중" 스위치 — config-repo 의 payment-service.yml 이 배달한다.
    // 0 이면 평소처럼 즉시 승인, 30000 이면 승인 한 건에 30초가 걸리는 '느려진 PG'가 된다.
    @Value("${payment.demo.delay-ms:0}")
    private long delayMs;

    @Override
    public PgApproval approve(String paymentKey, String pgOrderId, BigDecimal amount) {
        validateApprovalRequest(paymentKey, pgOrderId, amount);
        simulateSlowPg();

        // Toss 승인 성공 응답과 동일하게 승인 요청의 식별자와 금액을 돌려준다.
        return new PgApproval(paymentKey, pgOrderId, amount);
    }

    @Override
    public void cancel(String paymentKey) {
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalArgumentException("paymentKey는 필수입니다.");
        }
        simulateSlowPg();
    }

    private void validateApprovalRequest(String paymentKey, String pgOrderId, BigDecimal amount) {
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalArgumentException("paymentKey는 필수입니다.");
        }
        if (pgOrderId == null || pgOrderId.isBlank()) {
            throw new IllegalArgumentException("PG 주문번호는 필수입니다.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다.");
        }
    }

    private void simulateSlowPg() {
        if (delayMs <= 0) return;
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
