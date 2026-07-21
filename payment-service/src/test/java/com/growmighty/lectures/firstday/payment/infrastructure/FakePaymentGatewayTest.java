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
                BigDecimal.valueOf(10_000)
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
                " ", "order_123", BigDecimal.valueOf(10_000)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("0원 이하 금액은 승인할 수 없다")
    void approve_withNonPositiveAmount_throws() {
        assertThatThrownBy(() -> paymentGateway.approve(
                "payment-key-1", "order_123", BigDecimal.ZERO
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
