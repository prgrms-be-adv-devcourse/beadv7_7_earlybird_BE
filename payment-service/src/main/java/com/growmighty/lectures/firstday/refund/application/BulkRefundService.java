package com.growmighty.lectures.firstday.refund.application;

import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import com.growmighty.lectures.firstday.refund.domain.Refund;
import com.growmighty.lectures.firstday.refund.domain.RefundReason;
import com.growmighty.lectures.firstday.refund.domain.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BulkRefundService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    @Transactional
    public void plan(Long settlementId, List<Long> orderIds, RefundReason reason) {
        List<Payment> payments = paymentRepository.findAllPaidByOrderIds(orderIds);

        if (payments.isEmpty()) {
            return;
        }

        Set<Long> existingPaymentIds = new HashSet<>(
            refundRepository.findExistingPaymentIds(
                payments.stream()
                    .map(Payment::getPaymentId)
                    .toList()
            )
        );

        payments.stream()
            .filter(payment -> !existingPaymentIds.contains(payment.getPaymentId()))
            .forEach(payment -> registerPlannedRefund(payment, settlementId, reason));
    }

    private void registerPlannedRefund(Payment payment, Long settlementId, RefundReason reason) {
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
