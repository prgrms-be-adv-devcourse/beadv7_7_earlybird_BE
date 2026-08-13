package com.growmighty.lectures.firstday.refund.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RefundTest {

    private static final Long PAYMENT_ID = 1L;
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(10_000);

    @Test
    void 재시도_가능한_환불_실패는_RETRY_PENDING으로_전이한다() {
        Refund refund = requestedRefund();
        LocalDateTime now = LocalDateTime.of(2026, 8, 13, 12, 0);

        refund.scheduleRetry(now, 3, Duration.ofMinutes(5));

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.RETRY_PENDING);
        assertThat(refund.getRetryCount()).isEqualTo(1);
        assertThat(refund.getNextRetryAt()).isEqualTo(now.plusMinutes(5));
    }

    @Test
    void 최대_재시도_횟수를_초과하면_FAILED로_전이한다() {
        Refund refund = requestedRefund();
        LocalDateTime now = LocalDateTime.of(2026, 8, 13, 12, 0);

        refund.scheduleRetry(now, 1, Duration.ofMinutes(5));
        refund.startRequest();
        refund.scheduleRetry(now.plusMinutes(5), 1, Duration.ofMinutes(5));

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getRetryCount()).isEqualTo(1);
    }

    @Test
    void 재시도_대기_환불을_다시_요청하면_다음_재시도_시각을_초기화한다() {
        Refund refund = requestedRefund();
        LocalDateTime now = LocalDateTime.of(2026, 8, 13, 12, 0);
        refund.scheduleRetry(now, 3, Duration.ofMinutes(5));

        refund.startRequest();

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getNextRetryAt()).isNull();
    }

    private Refund requestedRefund() {
        return Refund.request(PAYMENT_ID, AMOUNT, RefundReason.USER_CANCEL);
    }
}
