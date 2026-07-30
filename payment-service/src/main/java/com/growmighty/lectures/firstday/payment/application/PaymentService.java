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
                    throw new IllegalStateException("이미 준비된 결제의 정보와 요청 정보가 다릅니다. orderId=" + orderId);
                }

                if (existingPayment.isReady()) {
                    return PaymentPreparationInfo.from(existingPayment);
                }

                if (existingPayment.isConfirming()) {
                    throw new PaymentConfirmationInProgressException(existingPayment.getPgOrderId());
                }

                if (existingPayment.isPaid()) {
                    throw new IllegalStateException("이미 결제가 완료된 주문입니다. orderId=" + orderId);
                }

                if (existingPayment.isFailed()) {
                    throw new IllegalStateException("실패한 결제입니다. 재결제 처리가 필요합니다. orderId=" + orderId);
                }

                if (existingPayment.isCancelled()) {
                    throw new IllegalStateException("취소된 결제입니다. orderId=" + orderId);
                }

                throw new IllegalStateException("지원하지 않는 결제 상태입니다. status=" + existingPayment.getStatus());
            })
            .orElseGet(() -> {
                Payment payment = Payment.ready(orderId, amount);
                return PaymentPreparationInfo.from(paymentRepository.save(payment));
            });
    }

    /**
     *
     * confirm 메소드 자체는 프론트에서 토스 결제창을 호출하고, 사용자가 결제를 승인한 후
     * 토스가 paymentKey와 orderId(PgOrderId), amount를 프론트에 반환합니다. 그 이후
     * 프론트가 Payment 서비스의 Post /api/v1/payments/confirm API를 호출합니다.
     * 그렇게 되면 Payment 서비스가 토스 승인 API를 호출하고, 성공하면 PAID 및 Outbox에 저장하는데
     * 현재 세미 프로젝트 상황에서는 프론트가 구현되지 않아 실제로 프론트에서 동작여부를 확인할 수 업습니다.
     *
     * 대신 백엔드에서 Curl을 통해 실제 동작함을 확인헀습니다.
     *
     * @param paymentKey
     * @param pgOrderId
     * @param amount
     * @return
     *
     */
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
