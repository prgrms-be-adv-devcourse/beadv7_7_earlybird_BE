package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentConfirmationTarget;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentInfo;
import com.growmighty.lectures.firstday.payment.application.exception.PaymentConfirmationInProgressException;
import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    // 결제 승인 상태 전이를 트랜잭션 단위로 처리하는 클래스
    private final PaymentConfirmationService  paymentConfirmationService;

    @Transactional
    public PaymentInfo prepare(
        @NonNull Long orderId,
        @NonNull String pgOrderId,
        @NonNull Long userId,
        @NonNull BigDecimal amount
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

    public PaymentInfo confirm(String paymentKey, String pgOrderId, BigDecimal amount) {
        PaymentConfirmationTarget target;

        /** 중복 요청 승인 방지용 */
        try {
            target = paymentConfirmationService.startConfirmation(pgOrderId, amount);
        } catch (OptimisticLockingFailureException e) {
            throw new PaymentConfirmationInProgressException(pgOrderId);
        }

        PaymentGateway.PgApproval approval = paymentGateway.approve(
            paymentKey,
            target.pgOrderId(),
            target.amount(),
            target.idempotencyKey()
        );

        return paymentConfirmationService.completeConfirmation(
            target.paymentId(),
            paymentKey,
            approval
        );
    }

    @Transactional
    public PaymentInfo cancel(Long paymentId) {
        Payment payment = findPayment(paymentId);

        paymentGateway.cancel(payment.getPaymentKey());
        payment.cancel();
        return PaymentInfo.from(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public PaymentInfo getPayment(Long paymentId) {
        Payment payment = findPayment(paymentId);
        return PaymentInfo.from(payment);
    }

    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 결제입니다. paymentId=" + paymentId));
    }
}
