package com.growmighty.lectures.firstday.refund.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.payment.domain.*;
import com.growmighty.lectures.firstday.refund.application.dto.RefundCancellationTarget;
import com.growmighty.lectures.firstday.refund.config.RefundRecoveryProperties;
import com.growmighty.lectures.firstday.refund.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RefundService {
    private final PaymentRepository  paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentStatusOutboxRepository  paymentStatusOutboxRepository;
    private final BulkRefundResultOutboxRepository bulkRefundResultOutboxRepository;
    private final RefundRecoveryProperties refundRecoveryProperties;
    private final ApplicationEventPublisher  applicationEventPublisher;

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
            payment.getPaymentKey().value(),
            refund.getReason(),
            refund.getCancelIdempotencyKey().value());
    }

    @Transactional
    public void scheduleRetry(Long refundId) {
        Refund refund = findRefund(refundId);
        refund.scheduleRetry(
            LocalDateTime.now(),
            refundRecoveryProperties.maximumRetryCount(),
            refundRecoveryProperties.retryDelay()
        );

        refundRepository.save(refund);
        recordBulkRefundResultIfCompleted(refund.getRefundRequestId());
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
        savePaymentStatusOutboxIfAbsent(payment.getPaymentId(), payment.getOrderId(), payment.getPgOrderId(), payment.getStatus());

        recordBulkRefundResultIfCompleted(refund.getRefundRequestId());
    }

    private void recordBulkRefundResultIfCompleted(Long refundRequestId) {
        if (refundRequestId == null
            || refundRepository.existsInProgressByRefundRequestId(refundRequestId)
        ) {
            return;
        }

        saveBulkRefundResultIfAbsent(
            refundRequestId,
            BulkRefundResultStatus.COMPLETED,
            refundRepository.existsCompletedByRefundRequestId(refundRequestId)
        );
        saveBulkRefundResultIfAbsent(
            refundRequestId,
            BulkRefundResultStatus.FAILED,
            refundRepository.existsFailedByRefundRequestId(refundRequestId)
        );
    }

    private void savePaymentStatusOutboxIfAbsent(Long paymentId, Long orderId, String pgOrderId, PaymentStatus status) {
        if(paymentStatusOutboxRepository.existsByPaymentIdAndPaymentStatus(paymentId, status)) {
            return;
        }

        PaymentStatusOutbox outbox = paymentStatusOutboxRepository.save(
            PaymentStatusOutbox.pending(
                paymentId,
                orderId,
                pgOrderId,
                status
            )
        );

        applicationEventPublisher.publishEvent(outbox);
    }

    private void saveBulkRefundResultIfAbsent(
        Long refundRequestId,
        BulkRefundResultStatus resultStatus,
        boolean resultExists
    ) {

        if (!resultExists) {
            return;
        }

        bulkRefundResultOutboxRepository.insertIfAbsent(
            refundRequestId,
            resultStatus
        );
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

        if (refund.reconcileFailed()) {
            refundRepository.save(refund);
            recordBulkRefundResultIfCompleted(refund.getRefundRequestId());
        }
    }

    @Transactional
    public RefundCancellationTarget startPlannedRefund(Long refundId) {
        Refund refund = findRefund(refundId);
        Payment payment = findPayment(refund.getPaymentId());

        refund.startRequest();
        refundRepository.save(refund);

        return new RefundCancellationTarget(
            refund.getId(),
            payment.getPaymentKey().value(),
            refund.getReason(),
            refund.getCancelIdempotencyKey().value()
        );
    }
}
