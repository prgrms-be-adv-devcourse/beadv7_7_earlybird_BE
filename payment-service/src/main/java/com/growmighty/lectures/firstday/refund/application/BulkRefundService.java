package com.growmighty.lectures.firstday.refund.application;

import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import com.growmighty.lectures.firstday.refund.domain.Refund;
import com.growmighty.lectures.firstday.refund.domain.RefundReason;
import com.growmighty.lectures.firstday.refund.domain.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BulkRefundService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    @Transactional
    public void plan(Long settlementId, List<Long> orderIds, RefundReason reason) {
        for (Long orderId : orderIds) {
            paymentRepository.findByOrderId(orderId)
                .filter(Payment::isPaid)
                .ifPresent(payment -> registerPlannedRefund(payment, settlementId, reason));
        }
    }

    private void registerPlannedRefund(Payment payment, Long settlementId, RefundReason reason) {
        if (refundRepository.findByPaymentId(payment.getPaymentId()).isPresent()) {
            return;
        }

        refundRepository.save(
            Refund.planned(
                payment.getPaymentId(),
                settlementId,
                payment.getAmount(),
                reason
            )
        );
    }
}
