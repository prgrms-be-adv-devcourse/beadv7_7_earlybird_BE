package com.growmighty.lectures.firstday.payment.infrastructure;

import com.growmighty.lectures.firstday.payment.application.PaymentGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

// Step 4의 배너 데모(@RefreshScope + @Value)와 같은 패턴.
// config-repo 값을 바꾸고 POST /actuator/refresh 하면 "재시작 없이" 지연을 켜고 끈다.
@RefreshScope
@Component
public class FakePaymentGateway implements PaymentGateway {
    private final AtomicLong sequence = new AtomicLong(1);

    // 실습용 "PG사 점검 중" 스위치 — config-repo 의 payment-service.yml 이 배달한다.
    // 0 이면 평소처럼 즉시 승인, 30000 이면 승인 한 건에 30초가 걸리는 '느려진 PG'가 된다.
    @Value("${payment.demo.delay-ms:0}")
    private long delayMs;

    @Override
    public PgApproval approve(BigDecimal amount) {
        simulateSlowPg();
        String transactionId = "PG-" + sequence.getAndIncrement();
        return new PgApproval(transactionId);
    }

    @Override
    public void cancel(String pgTransactionId) {
        simulateSlowPg();
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
