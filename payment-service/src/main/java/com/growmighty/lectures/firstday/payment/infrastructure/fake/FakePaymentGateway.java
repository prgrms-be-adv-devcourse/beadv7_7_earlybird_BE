package com.growmighty.lectures.firstday.payment.infrastructure.fake;

import com.growmighty.lectures.firstday.payment.application.PaymentGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// Step 4의 배너 데모(@RefreshScope + @Value)와 같은 패턴.
// config-repo 값을 바꾸고 POST /actuator/refresh 하면 "재시작 없이" 지연을 켜고 끈다.
@RefreshScope
@Component
@ConditionalOnProperty(
    name = "payment.gateway",
    havingValue = "fake"
)
public class FakePaymentGateway implements PaymentGateway {

    private final ConcurrentMap<String, ApprovalAttempt> approvalsByIdempotencyKey =
        new ConcurrentHashMap<>();

    // 실습용 "PG사 점검 중" 스위치 — config-repo 의 payment-service.yml 이 배달한다.
    // 0 이면 평소처럼 즉시 승인, 30000 이면 승인 한 건에 30초가 걸리는 '느려진 PG'가 된다.
    @Value("${payment.demo.delay-ms:0}")
    private long delayMs;

    @Override
    public PgApproval approve(String paymentKey, String pgOrderId, BigDecimal amount, String idempotencyKey) {
        validateApprovalRequest(paymentKey, pgOrderId, amount, idempotencyKey);

        ApprovalAttempt newAttempt = new ApprovalAttempt(
            paymentKey,
            pgOrderId,
            amount
        );

        ApprovalAttempt existingAttempt = approvalsByIdempotencyKey.putIfAbsent(
            idempotencyKey,
            newAttempt
        );

        if(existingAttempt == null) {
            try {
                simulateSlowPg();

                PgApproval pgApproval = new PgApproval(
                    paymentKey,
                    pgOrderId,
                    amount
                );

                newAttempt.complete(pgApproval);

                return pgApproval;
            } catch (RuntimeException e) {
                approvalsByIdempotencyKey.remove(
                    idempotencyKey,
                    newAttempt
                );

                throw e;
            }
        }

        if (!existingAttempt.matches(
            paymentKey,
            pgOrderId,
            amount
        )) {
            throw new IllegalStateException("동일한 멱등키로 서로 다른 승인 요청을 보낼 수 없습니다.");
        }

        if (existingAttempt.isProcessing()) {
            throw new IllegalStateException("동일한 멱등키의 승인 요청이 처리 중입니다.");
        }




        // Toss 승인 성공 응답과 동일하게 승인 요청의 식별자와 금액을 돌려준다.
        return existingAttempt.getApproval();
    }

    private static final class ApprovalAttempt {
        private final String paymentKey;
        private final String pgOrderId;
        private final BigDecimal amount;

        private volatile PgApproval approval;

        private ApprovalAttempt(String paymentKey, String pgOrderId, BigDecimal amount) {
            this.paymentKey = paymentKey;
            this.pgOrderId = pgOrderId;
            this.amount = amount;
        }

        private boolean matches(String paymentKey, String pgOrderId, BigDecimal amount) {
            return this.paymentKey.equals(paymentKey)
                && this.pgOrderId.equals(pgOrderId)
                && this.amount.compareTo(amount) == 0;
        }

        // 추가 : paymentKey 기준 Fake PG 승인 이력 조회
        private boolean hasPaymentKey(String paymentKey) {
            return this.paymentKey.equals(paymentKey);
        }

        private boolean isProcessing() {
            return approval == null;
        }

        private void complete(PgApproval approval) {
            this.approval = approval;
        }

        private PgApproval getApproval() {
            if(approval == null) {
                throw new IllegalStateException("승인 처리 결과가 아직 없습니다.");
            }

            return approval;
        }

        // 추가 : 완료된 Fake PG 승인 결과를 조회 응답으로 변환
        private PgPayment getPayment() {
            PgApproval approval = getApproval();

            return new PgPayment(
                approval.paymentKey(),
                approval.pgOrderId(),
                approval.amount(),
                PgPaymentStatus.COMPLETED
            );
        }
    }

    @Override
    public PgPayment getPayment(String paymentKey) {
        validatePaymentKey(paymentKey); // <-- paymentKey 검증

        return approvalsByIdempotencyKey.values().stream() // <-- 승인 이력에서 paymentKey 조회
            .filter(attempt -> attempt.hasPaymentKey(paymentKey))
            .findFirst()
            .map(ApprovalAttempt::getPayment)
            .orElseThrow(() -> new IllegalStateException(
                "paymentKey에 해당하는 승인 이력이 없습니다. paymentKey=" + paymentKey
            ));
    }

    private void validateApprovalRequest(String paymentKey, String pgOrderId, BigDecimal amount, String idempotencyKey) {
        validatePaymentKey(paymentKey);

        if (pgOrderId == null || pgOrderId.isBlank()) {
            throw new IllegalArgumentException("PG 주문번호는 필수입니다.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("승인 멱등키는 필수입니다.");
        }
    }

    private void validatePaymentKey(String paymentKey) {
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalArgumentException("paymentKey는 필수입니다.");
        }
    }

    private void simulateSlowPg() {
        if (delayMs <= 0) return;
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FAKE PG 승인 처리가 중단되었습니다. ", e);
        }
    }
}
