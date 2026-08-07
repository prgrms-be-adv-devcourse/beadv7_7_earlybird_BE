package com.growmighty.lectures.firstday.refund.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import com.growmighty.lectures.firstday.refund.application.dto.RefundCancellationTarget;
import com.growmighty.lectures.firstday.refund.domain.Refund;
import com.growmighty.lectures.firstday.refund.domain.RefundReason;
import com.growmighty.lectures.firstday.refund.domain.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundService {
    private final PaymentRepository  paymentRepository;
    private final RefundRepository refundRepository;

    @Transactional
    public RefundCancellationTarget startRefund(Long paymentId, RefundReason reason) {
        Payment payment = findPaidPayment(paymentId);

        Refund refund = refundRepository.findByPaymentId(payment.getPaymentId())
            .map(existingRefund -> {
                if (!existingRefund.isRequested()) {
                    throw new IllegalStateException("이미 처리 이력이 있는 환불입니다. status = " + existingRefund.getStatus());
                }

                return existingRefund;
            })
            .orElseGet(() -> refundRepository.save(
                Refund.request(
                    payment.getPaymentId(),
                    payment.getAmount(),
                    reason
                )
            ));

        return new RefundCancellationTarget(
            refund.getId(),
            payment.getPaymentKey(),
            refund.getReason(),
            refund.getCancelIdempotencyKey()
        );
    }

    private Payment findPaidPayment(Long paymentId) {
        Payment payment = findPayment(paymentId);

        if (!payment.isPaid()) {
            throw new IllegalStateException("PAID 상태의 결제만 환불할 수 있습니다. status = " + payment.getStatus());
        }

        return payment;
    }

    @Transactional
    public void completeRefund(Long refundId) {
        Refund refund = findRefund(refundId);
        Payment payment = findPayment(refund.getPaymentId());

        refund.complete();
        payment.cancel();

        refundRepository.save(refund);
        paymentRepository.save(payment);
    }

    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 결제입니다. paymentId = " + paymentId));
    }

    private Refund findRefund(Long refundId) {
        return refundRepository.findById(refundId).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 환불입니다. refundId = " + refundId));
    }

    @Transactional
    public void failRefund(Long refundId) {
        Refund refund = findRefund(refundId);

        if (refund.reconcileFailed()) { // <-- 이미 정합화된 환불은 no-op
            refundRepository.save(refund);
        }
    }
}
