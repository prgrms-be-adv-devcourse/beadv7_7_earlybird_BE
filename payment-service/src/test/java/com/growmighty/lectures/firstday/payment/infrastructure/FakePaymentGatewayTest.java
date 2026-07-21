package com.growmighty.lectures.firstday.payment.infrastructure;

import com.growmighty.lectures.firstday.payment.application.PaymentGateway;
import com.growmighty.lectures.firstday.payment.infrastructure.fake.FakePaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
class FakePaymentGatewayTest {

    private final FakePaymentGateway paymentGateway = new FakePaymentGateway();

    @Test
    @DisplayName("승인 성공 시 토스 승인 응답처럼 paymentKey, orderId, amount를 반환한다")
    void approve_returnsTossLikeApproval() {
        PaymentGateway.PgApproval approval = paymentGateway.approve(
                "payment-key-1",
                "order_123",
                BigDecimal.valueOf(10_000),
                "approve-key-1"
        );

        assertThat(approval.paymentKey()).isEqualTo("payment-key-1");
        assertThat(approval.pgOrderId()).isEqualTo("order_123");
        assertThat(approval.amount()).isEqualByComparingTo("10000");
        log.info("fake PG approved: paymentKey={}, pgOrderId={}, amount={}",
                approval.paymentKey(), approval.pgOrderId(), approval.amount());
    }

    @Test
    @DisplayName("paymentKey 없이 승인할 수 없다")
    void approve_withoutPaymentKey_throws() {
        assertThatThrownBy(() -> paymentGateway.approve(
                " ", "order_123", BigDecimal.valueOf(10_000), "approve-key-1"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("0원 이하 금액은 승인할 수 없다")
    void approve_withNonPositiveAmount_throws() {
        assertThatThrownBy(() -> paymentGateway.approve(
                "payment-key-1", "order_123", BigDecimal.ZERO, "approve-key-1"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("같은 멱등키로 승인 요청을 재시도하면 최초 승인 결과를 반환한다")
    void approve_withSameIdempotencyKey_returnsFirstApproval() {
        PaymentGateway.PgApproval first = paymentGateway.approve(
            "payment-key-1",
            "order_123",
            BigDecimal.valueOf(10_000),
            "approve-key-1"
        );

        PaymentGateway.PgApproval retried = paymentGateway.approve(
            "payment-key-1",
            "order_123",
            BigDecimal.valueOf(10_000),
            "approve-key-1"
        );

        assertThat(retried).isEqualTo(first);
    }

    @Test
    @DisplayName("같은 멱등키로 다른 승인 요청을 보내면 실패한다")
    void approve_withSameIdempotencyKeyAndDifferentRequest_throws() {
        paymentGateway.approve(
            "payment-key-1",
            "order_123",
            BigDecimal.valueOf(10_000),
            "approve-key-1"
        );

        assertThatThrownBy(() -> paymentGateway.approve(
            "payment-key-2",
            "order_123",
            BigDecimal.valueOf(10_000),
            "approve-key-1"
        )).isInstanceOf(IllegalStateException.class);
    }
}
