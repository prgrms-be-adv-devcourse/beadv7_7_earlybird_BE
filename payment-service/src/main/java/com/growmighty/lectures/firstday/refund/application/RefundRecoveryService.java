package com.growmighty.lectures.firstday.refund.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.payment.application.PaymentGateway;
import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import com.growmighty.lectures.firstday.refund.domain.Refund;
import com.growmighty.lectures.firstday.refund.domain.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundRecoveryService {
    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final RefundService refundService;

    public void recover(Long refundId) {
        Refund refund = refundRepository.findById(refundId)
            .orElseThrow(() -> new  EntityNotFoundException("존재하지 않는 환불입니다. refundId = " + refundId));

        if (!refund.isRequested()) {
            return;
        }

        Payment payment = paymentRepository.findById(refund.getPaymentId())
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 결제입니다. paymentId = " + refund.getPaymentId()));

        PaymentGateway.PgPayment pgPayment = paymentGateway.getPayment(payment.getPaymentKey());

        switch (pgPayment.status()) {
            case CANCELLED -> refundService.completeRefund(refundId);
            case COMPLETED, FAILED, EXPIRED -> refundService.failRefund(refundId);
            case PENDING -> {
                refundService.scheduleRetry(refundId);
            }
        }
    }
}
