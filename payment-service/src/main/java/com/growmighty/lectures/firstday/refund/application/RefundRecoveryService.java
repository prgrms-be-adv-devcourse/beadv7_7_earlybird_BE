package com.growmighty.lectures.firstday.refund.application;

import com.growmighty.lectures.firstday.payment.application.PaymentGateway;
import com.growmighty.lectures.firstday.refund.application.dto.RefundRecoveryTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundRecoveryService {
    private final PaymentGateway paymentGateway;
    private final RefundService refundService;

    public void recover(RefundRecoveryTarget target) {
        PaymentGateway.PgPayment pgPayment = paymentGateway.getPayment(target.paymentKey().value());

        switch (pgPayment.status()) {
            case CANCELLED -> refundService.completeRefund(target.refundId());
            case COMPLETED, FAILED, EXPIRED -> refundService.failRefund(target.refundId());
            case PENDING -> {
                refundService.scheduleRetry(target.refundId());
            }
        }
    }
}
