package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentConfirmationTarget;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentInfo;
import com.growmighty.lectures.firstday.payment.application.exception.PaymentConfirmationInProgressException;
import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/** 클래스 별도 생성 이유 : PaymentService 내부 메서드 끼리 호출하면 트랜잭션이 적용되지 않아서.
 *  결제 승인 상태 전이를 트랜잭션 단위로 처리하는 클래스
 *  외부 PG 호출은 담당하지 않음
 * */

@Service
@RequiredArgsConstructor
public class PaymentConfirmationService {

    private final PaymentRepository paymentRepository;
    private final PaymentStatusOutboxAppender paymentStatusOutboxAppender;

    /**
     * 이 메서드가 끝나면 트랜잭션도 끝남 -> 외부 PG 호출 동안 DB 트랜잭션을 붙잡지 않음
     * 승인 멱등키와 결제정보를 반환하고, Ready -> Confirming 상태를 선점하는 메서드
     */
    @Transactional
    public PaymentConfirmationTarget startConfirmation(String paymentKey, String pgOrderId, BigDecimal requestedAmount) {
        Payment payment = paymentRepository.findByPgOrderId(pgOrderId)
            .orElseThrow(() -> new EntityNotFoundException("준비된 결제가 없습니다. pgOrderId = " + pgOrderId));

        if (payment.isPaid()) {
            throw new IllegalStateException("이미 승인된 결제입니다. pgOrderId = " + pgOrderId);
        }

        if(payment.getAmount().compareTo(requestedAmount) != 0) {
            throw new IllegalStateException("결제 요청 금액이 일치하지 않습니다.");
        }

        if (payment.isConfirming()) {
            throw new PaymentConfirmationInProgressException(pgOrderId);
        }

        payment.startConfirming(paymentKey);
        paymentRepository.save(payment);

        return new PaymentConfirmationTarget(
            payment.getPaymentId(),
            payment.getPgOrderId(),
            payment.getAmount(),
            payment.getApproveIdempotencyKey().value()
        );
    }

    /**
     * PG 승인 응답을 검증한 후 결제를 PAID 상태로 완료
     */
    @Transactional
    public PaymentInfo completeConfirmation(Long paymentId, String requestedPaymentKey, PaymentGateway.PgApproval approval) {
        Payment payment = findPayment(paymentId);

        if (!requestedPaymentKey.equals(approval.paymentKey())) {
            throw new IllegalStateException("PG paymentKey가 일치하지 않습니다.");
        }

        payment.validateApproval(
            approval.paymentKey(),
            approval.pgOrderId(),
            approval.amount()
        );

        payment.confirm(approval.paymentKey());
        savePaymentAndAppendOutbox(payment);
        return PaymentInfo.from(payment);
    }

    /**
     * 승인 실패,
     */
    @Transactional
    public void failConfirmation(Long paymentId) {
        Payment payment = findPayment(paymentId);

        if (payment.reconcileFailed()) {
            savePaymentAndAppendOutbox(payment);
        }
    }

    /**
     * 승인 응답 처리 경합 후 다른 경로에서 확정한 PAID 결제 결과 조회
     */
    @Transactional(readOnly = true)
    public Optional<PaymentInfo> findPaidPaymentInfo(Long paymentId) {
        Payment payment = findPayment(paymentId);

        if (!payment.isPaid()) {
            return Optional.empty();
        }

        return Optional.of(PaymentInfo.from(payment));
    }

    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 결제입니다. paymentId = " + paymentId));
    }

    private void savePaymentAndAppendOutbox(Payment payment) {
        paymentRepository.save(payment);
        paymentStatusOutboxAppender.appendIfAbsent(payment);
    }
}
