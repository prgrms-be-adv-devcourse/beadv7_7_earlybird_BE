package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentConfirmationTarget;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentInfo;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentRecoveryTarget;
import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/** 클래스 별도 생성 이유 : PaymentService 내부 메서드 끼리 호출하면 트랜잭션이 적용되지 않아서.
 *  결제 승인 상태 전이를 트랜잭션 단위로 처리하는 클래스
 *  외부 PG 호출은 담당하지 않음
 * */

@Service
@RequiredArgsConstructor
public class PaymentConfirmationService {

    private final PaymentRepository paymentRepository;

    /**
     * PG 승인 전에 결제를 CONFIRMING 상태로 선점,
     * 이 메서드가 끝나면 트랜잭션도 끝남 -> 외부 PG 호출 동안 DB 트랜잭션을 붙잡지 않음
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
            throw new IllegalStateException("이미 승인 처리 중인 결제입니다. 잠시 후 다시 조회해주세요. pgOrderId = " + pgOrderId);
        }

        payment.startConfirming(paymentKey);
        paymentRepository.save(payment);

        return new PaymentConfirmationTarget(
            payment.getPaymentId(),
            payment.getPgOrderId(),
            payment.getAmount(),
            payment.getApproveIdempotencyKey()
        );
    }

    /**
     * PG 승인 응답을 검증한 후 결제를 PAID 상태로 완료
     */
    @Transactional
    public PaymentInfo completeConfirmation(
        Long paymentId,
        String requestedPaymentKey,
        PaymentGateway.PgApproval approval) {
        Payment payment = findPayment(paymentId);

        if (!requestedPaymentKey.equals(approval.paymentKey())) {
            throw new IllegalStateException("PG paymentKey가 일치하지 않습니다.");
        }

        if (!payment.getPgOrderId().equals(approval.pgOrderId())) {
            throw new IllegalStateException("PG 주문번호가 일치하지 않습니다. expected = " + payment.getPgOrderId() + ", actual = " + approval.pgOrderId());
        }

        if (payment.getAmount().compareTo(approval.amount()) != 0) {
            throw new IllegalStateException("PG 승인 금액이 일치하지 않습니다. expected = " + approval.amount() + ", actual = " + payment.getAmount());
        }

        payment.confirm(approval.paymentKey());

        return PaymentInfo.from(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public PaymentRecoveryTarget getRecoveryTarget(Long paymentId) {
        Payment payment = findPayment(paymentId);

        if(!payment.isConfirming()) {
            throw new IllegalStateException("CONFIRMING 상태의 결제만 복구할 수 있습니다. 현재 상태 : " + payment.getStatus());
        }

        if (payment.getPaymentKey() == null || payment.getPaymentKey().isBlank()) {
            throw new IllegalStateException("CONFIRMING 상태의 결제에 paymentKey가 없습니다. paymentId = " + paymentId);
        }

        return new PaymentRecoveryTarget(
            payment.getPaymentId(),
            payment.getPaymentKey()
        );
    }

    @Transactional
    public void failConfirmation(Long paymentId) {

        Payment payment = findPayment(paymentId);

        payment.fail();
        paymentRepository.save(payment);
    }

    @Transactional
    public void failConfirmationIfExpired(
        Long paymentId,
        LocalDateTime now,
        Duration maximumConfirmingDuration
    ) {
        Payment payment = findPayment(paymentId);

        if (!payment.isConfirmingExpired(now, maximumConfirmingDuration)) {
            return;
        }

        payment.fail();
        paymentRepository.save(payment);
    }

    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 결제입니다. paymentId = " + paymentId));
    }
}
