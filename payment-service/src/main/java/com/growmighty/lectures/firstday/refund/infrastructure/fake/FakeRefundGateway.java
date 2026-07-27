package com.growmighty.lectures.firstday.refund.infrastructure.fake;

import com.growmighty.lectures.firstday.refund.application.port.RefundGateway;
import com.growmighty.lectures.firstday.refund.domain.RefundReason;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "payment.gateway",
    havingValue = "fake",
    matchIfMissing = true
)
public class FakeRefundGateway implements RefundGateway {

    @Override
    public void refund(String paymentKey, RefundReason refundReason) {
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalArgumentException("paymentKey는 필수입니다.");
        }

        if (refundReason == null) {
            throw new IllegalStateException("환불 사유는 필수입니다.");
        }
    }
}
