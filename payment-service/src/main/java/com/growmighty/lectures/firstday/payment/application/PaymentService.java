package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentInfo;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentPreparationInfo;
import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentApprovalSagaOrchestrator paymentApprovalSagaService;
    private final PaymentPreparationService paymentPreparationService;

    public PaymentPreparationInfo prepare(
        @NonNull Long userId,
        @NonNull Long orderId,
        @NonNull BigDecimal amount
    ) {
        try {
            return paymentPreparationService.prepare(userId, orderId, amount);
        } catch (DataIntegrityViolationException exception) {
            return paymentPreparationService.getExistingPayment(userId, orderId, amount);
        }
    }

    /**
     *
     * confirm 메소드 자체는 프론트에서 토스 결제창을 호출하고, 사용자가 결제를 승인한 후
     * 토스가 paymentKey와 orderId(PgOrderId), amount를 프론트에 반환합니다. 그 이후
     * 프론트가 Payment 서비스의 Post /api/v1/payments/confirm API를 호출합니다.
     * 그렇게 되면 Payment 서비스가 토스 승인 API를 호출하고, 성공하면 PAID 및 Outbox에 저장하는데
     * 현재 세미 프로젝트 상황에서는 프론트가 구현되지 않아 실제로 프론트에서 동작여부를 확인할 수 업습니다.
     * <p>
     * 대신 백엔드에서 Curl을 통해 실제 동작함을 확인헀습니다.
     *
     * @param paymentKey
     * @param pgOrderId
     * @param amount
     * @return
     *
     */
    public PaymentInfo confirm(Long requesterId, String paymentKey, String pgOrderId, BigDecimal amount) {

        Payment payment = paymentRepository.findByPgOrderId(pgOrderId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 결제입니다. pgOrderId=" + pgOrderId));
        validateRequesterByPayment(requesterId, payment);
        return paymentApprovalSagaService.approve(paymentKey, pgOrderId, amount);
    }

    @Transactional(readOnly = true)
    public PaymentInfo getPayment(Long paymentId, Long requesterId) {
        Payment payment = findPayment(paymentId);
        validateRequesterByPayment(requesterId, payment);
        return PaymentInfo.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentInfo getPaymentForInternal(Long paymentId) {
        return PaymentInfo.from(findPayment(paymentId));
    }

    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 결제입니다. paymentId=" + paymentId));
    }

    private static void validateRequesterByPayment(Long requesterId, Payment payment) {
        if (!payment.isOwnedBy(requesterId)) {
            throw new IllegalStateException("결제 소유자가 일치하지 않습니다.");
        }
    }

    @Transactional(readOnly = true)
    public PaymentInfo getPaymentByOrderId(Long orderId, Long requesterId) {
        Payment payment = findPaymentByOrderId(orderId);
        validateRequesterByPayment(requesterId, payment);
        return  PaymentInfo.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentInfo getPaymentByOrderIdInternal(Long orderId) {
        Payment payment = findPaymentByOrderId(orderId);
        return  PaymentInfo.from(payment);
    }

    private Payment findPaymentByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 주문의 결제입니다. " + orderId));
    }
}
