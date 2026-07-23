package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentConfirmationTarget;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentInfo;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentPreparationInfo;
import com.growmighty.lectures.firstday.payment.application.exception.PaymentConfirmationInProgressException;
import com.growmighty.lectures.firstday.payment.application.port.OrderStatusPort;
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
    private final OrderStatusPort  orderStatusPort;

    @Transactional
    public PaymentPreparationInfo prepare(
        @NonNull Long orderId,
        @NonNull BigDecimal amount
    ) {
        return paymentRepository.findByOrderId(orderId)
            .map(existingPayment -> {
                boolean sameRequest = existingPayment.getAmount().compareTo(amount) == 0;

                if (!sameRequest) {
                    throw new IllegalStateException(
                        "이미 준비된 결제의 정보와 요청 정보가 다릅니다. orderId=" + orderId
                    );
                }

                return PaymentPreparationInfo.from(existingPayment);
            })
            .orElseGet(() -> {
                Payment payment = Payment.ready(orderId, amount);
                return PaymentPreparationInfo.from(paymentRepository.save(payment));
            });
    }

    public PaymentInfo confirm(String paymentKey, String pgOrderId, BigDecimal amount) {
        PaymentConfirmationTarget target;

        /** 중복 요청 승인 방지용 */
        try {
            target = paymentConfirmationService.startConfirmation(paymentKey, pgOrderId, amount);
        } catch (OptimisticLockingFailureException e) {
            throw new PaymentConfirmationInProgressException(pgOrderId);
        }

        PaymentGateway.PgApproval approval = paymentGateway.approve(
            paymentKey,
            target.pgOrderId(),
            target.amount(),
            target.idempotencyKey()
        );

        PaymentInfo paymentInfo = paymentConfirmationService.completeConfirmation(
            target.paymentId(),
            paymentKey,
            approval
        );

        orderStatusPort.notifyStatus(
            paymentInfo.orderId(),
            paymentInfo.status()
        );

        return paymentInfo;
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
