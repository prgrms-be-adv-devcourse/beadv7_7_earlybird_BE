package com.growmighty.lectures.firstday.refund.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import com.growmighty.lectures.firstday.refund.application.port.RefundGateway;
import com.growmighty.lectures.firstday.refund.domain.Refund;
import com.growmighty.lectures.firstday.refund.domain.RefundReason;
import com.growmighty.lectures.firstday.refund.domain.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundService {
    private final PaymentRepository  paymentRepository;
    private final RefundRepository refundRepository;
    private final RefundGateway refundGateway;

    @Transactional
    public Refund refund(UUID orderId, RefundReason reason) {
        Payment payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 결제입니다. orderId=" + orderId));

        if (!payment.isPaid()) {
            throw new IllegalStateException("PAID 상태의 결제만 환불할 수 있습니다. status = " + payment.getStatus());
        }

        Refund refund = refundRepository.save(
            Refund.request(
                payment.getPaymentId(),
                payment.getAmount(),
                reason
            )
        );

        refundGateway.refund(payment.getPaymentKey(), reason);

        refund.complete();
        payment.cancel();

        paymentRepository.save(payment);
        return refundRepository.save(refund);
    }
}
