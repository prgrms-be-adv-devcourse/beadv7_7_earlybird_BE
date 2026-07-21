package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentInfo;
import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    @Transactional
    public PaymentInfo prepare(
        Long orderId,
        String pgOrderId,
        Long userId,
        BigDecimal amount
    ) {
        return paymentRepository.findByOrderId(orderId)
            .map(existingPayment -> {
                boolean sameRequest =
                    existingPayment.getPgOrderId().equals(pgOrderId)
                        && existingPayment.getUserId().equals(userId)
                        && existingPayment.getAmount().compareTo(amount) == 0;

                if (!sameRequest) {
                    throw new IllegalStateException(
                        "이미 준비된 결제의 정보와 요청 정보가 다릅니다. orderId=" + orderId
                    );
                }

                return PaymentInfo.from(existingPayment);
            })
            .orElseGet(() -> {
                Payment payment = Payment.ready(
                    orderId,
                    pgOrderId,
                    userId,
                    amount
                );

                return PaymentInfo.from(paymentRepository.save(payment));
            });
    }

    @Transactional
    public PaymentInfo confirm(String paymentKey, String pgOrderId) {
        Payment payment = paymentRepository.findByPgOrderId(pgOrderId)
            .orElseThrow(() -> new EntityNotFoundException("준비된 결제가 없습니다. pgOrderId = " + pgOrderId));

        if (payment.isPaid()) {
            return PaymentInfo.from(payment);
        }

        payment.startConfirming();

        PaymentGateway.PgApproval approval = paymentGateway.approve(
            paymentKey,
            payment.getPgOrderId(),
            payment.getAmount()
        );

        if (!paymentKey.equals(approval.paymentKey())
            || !payment.getPgOrderId().equals(approval.pgOrderId())
            || payment.getAmount().compareTo(approval.amount()) != 0) {
            throw new IllegalStateException("PG 승인 응답이 저장된 결제 정보와 일치하지 않습니다.");
        }

        payment.confirm(approval.paymentKey());
        return PaymentInfo.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentInfo cancel(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 결제입니다. paymentId=" + paymentId));

        paymentGateway.cancel(payment.getPaymentKey());
        payment.cancel();
        return PaymentInfo.from(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public PaymentInfo getPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 결제입니다. paymentId=" + paymentId));
        return PaymentInfo.from(payment);
    }
}
