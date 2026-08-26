package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.common.exception.BusinessException;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentInfo;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentPreparationInfo;
import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
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
            throw new BusinessException(HttpStatus.FORBIDDEN, "결제 소유자가 일치하지 않습니다.");
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
