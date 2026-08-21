package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentPreparationInfo;
import com.growmighty.lectures.firstday.payment.application.exception.PaymentConfirmationInProgressException;
import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PaymentPreparationService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentPreparationInfo prepare(
        Long userId,
        Long orderId,
        BigDecimal amount
    ) {
        return paymentRepository.findByOrderId(orderId)
            .map(payment -> toPreparationInfo(payment, userId, orderId, amount))
            .orElseGet(() -> PaymentPreparationInfo.from(
                paymentRepository.save(Payment.ready(userId, orderId, amount))
            ));
    }

    @Transactional(readOnly = true)
    public PaymentPreparationInfo getExistingPayment(
        Long userId,
        Long orderId,
        BigDecimal amount
    ) {
        Payment payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new EntityNotFoundException(
                "존재하지 않는 주문의 결제입니다. orderId=" + orderId
            ));

        return toPreparationInfo(payment, userId, orderId, amount);
    }

    private PaymentPreparationInfo toPreparationInfo(
        Payment payment,
        Long userId,
        Long orderId,
        BigDecimal amount
    ) {
        if (!userId.equals(payment.getUserId())) {
            throw new IllegalStateException("주문 소유자가 일치하지 않습니다. userId=" + userId);
        }

        if (payment.getAmount().compareTo(amount) != 0) {
            throw new IllegalStateException(
                "이미 준비된 결제의 정보와 요청 정보가 다릅니다. orderId=" + orderId
            );
        }

        if (payment.isReady()) {
            return PaymentPreparationInfo.from(payment);
        }

        if (payment.isConfirming()) {
            throw new PaymentConfirmationInProgressException(payment.getPgOrderId());
        }

        if (payment.isPaid()) {
            throw new IllegalStateException("이미 결제가 완료된 주문입니다. orderId=" + orderId);
        }

        if (payment.isFailed()) {
            throw new IllegalStateException(
                "실패한 결제입니다. 재결제 처리가 필요합니다. orderId=" + orderId
            );
        }

        if (payment.isCancelled()) {
            throw new IllegalStateException("취소된 결제입니다. orderId=" + orderId);
        }

        throw new IllegalStateException("지원하지 않는 결제 상태입니다. status=" + payment.getStatus());
    }
}
