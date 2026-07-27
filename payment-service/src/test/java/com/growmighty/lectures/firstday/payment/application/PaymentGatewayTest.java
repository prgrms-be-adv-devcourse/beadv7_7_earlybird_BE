package com.growmighty.lectures.firstday.payment.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentGatewayTest {

    @Test
    void 토스_결제_상태를_내부_PG_상태로_변환한다() {
        assertThat(PaymentGateway.PgPaymentStatus.fromTossStatus("DONE"))
            .isEqualTo(PaymentGateway.PgPaymentStatus.COMPLETED);
        assertThat(PaymentGateway.PgPaymentStatus.fromTossStatus("ABORTED"))
            .isEqualTo(PaymentGateway.PgPaymentStatus.FAILED);
        assertThat(PaymentGateway.PgPaymentStatus.fromTossStatus("EXPIRED"))
            .isEqualTo(PaymentGateway.PgPaymentStatus.EXPIRED);
        assertThat(PaymentGateway.PgPaymentStatus.fromTossStatus("CANCELED"))
            .isEqualTo(PaymentGateway.PgPaymentStatus.CANCELLED);
    }

    @Test
    void 알_수_없거나_비어있는_토스_상태는_PENDING으로_변환한다() {
        assertThat(PaymentGateway.PgPaymentStatus.fromTossStatus("UNKNOWN"))
            .isEqualTo(PaymentGateway.PgPaymentStatus.PENDING);
        assertThat(PaymentGateway.PgPaymentStatus.fromTossStatus(null))
            .isEqualTo(PaymentGateway.PgPaymentStatus.PENDING);
    }
}
