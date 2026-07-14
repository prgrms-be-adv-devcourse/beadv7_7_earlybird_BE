package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.application.dto.PaymentInfo;
import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
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
    public PaymentInfo pay(BigDecimal amount) {
        Payment payment = Payment.ready(amount);
        try {
            PaymentGateway.PgApproval approval = paymentGateway.approve(amount);
            payment.approve(approval.transactionId());
        } catch (RuntimeException e) {
            payment.fail();
            paymentRepository.save(payment);
            throw new IllegalStateException("결제 승인에 실패했습니다. amount=" + amount, e);
        }
        return PaymentInfo.from(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentInfo cancel(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 결제입니다. paymentId=" + paymentId));

        paymentGateway.cancel(payment.getPgTransactionId());
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
