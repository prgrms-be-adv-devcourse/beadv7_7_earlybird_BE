package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentConfirmationTarget;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentInfo;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentRecoveryTarget;
import com.growmighty.lectures.firstday.payment.application.exception.PaymentConfirmationInProgressException;
import com.growmighty.lectures.firstday.payment.config.PaymentRecoveryProperties;
import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private final PaymentRecoveryProperties paymentRecoveryProperties;

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
            payment.getApproveIdempotencyKey().value() // <-- PG에는 평문 값만 전달
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
        Payment savedPayment = paymentRepository.save(payment);
        savePaymentStatusOutboxIfAbsent(savedPayment); // <-- 동일 상태 Outbox 중복 저장 방지

        return PaymentInfo.from(savedPayment);
    }

    /**
     * 승인 응답 처리 경합 후 다른 경로에서 확정한 PAID 결제 결과 조회
     * @param paymentId
     * @return
     */
    @Transactional(readOnly = true)
    public Optional<PaymentInfo> findPaidPaymentInfo(Long paymentId) {
        Payment payment = findPayment(paymentId);

        if (!payment.isPaid()) {
            return Optional.empty();
        }

        return Optional.of(PaymentInfo.from(payment));
    }

    /**
     * 승인 실패,
     */
    @Transactional
    public void failConfirmation(Long paymentId) {
        Payment payment = findPayment(paymentId);

        if (payment.reconcileFailed()) {
            Payment savedPayment = paymentRepository.save(payment);
            savePaymentStatusOutboxIfAbsent(savedPayment);
        }
    }

    @Transactional
    public void expireReadyPayment(Long paymentId) {
        Payment payment = findPayment(paymentId);

        if (payment.failIfReadyExpired(
            LocalDateTime.now(),
            paymentRecoveryProperties.readyTimeOut()
        )) {
            Payment savedPayment = paymentRepository.save(payment);
            savePaymentStatusOutboxIfAbsent(savedPayment);
        }
    }

    @Transactional(readOnly = true)
    public PaymentRecoveryTarget getRecoveryTarget(Long paymentId) {
        Payment payment = findPayment(paymentId);

        if(!payment.isConfirming()) {
            throw new IllegalStateException("CONFIRMING 상태의 결제만 복구할 수 있습니다. 현재 상태 : " + payment.getStatus());
        }

        if (payment.getPaymentKey() == null) { // <-- VO는 생성 시 공백을 거부함
            throw new IllegalStateException("CONFIRMING 상태의 결제에 paymentKey가 없습니다. paymentId = " + paymentId);
        }

        return new PaymentRecoveryTarget(
            payment.getPaymentId(),
            payment.getPaymentKey().value() // <-- PG 조회에는 평문 값만 전달
        );
    }

    @Transactional
    public Optional<PaymentInfo> reconcile(PaymentGateway.PgPayment pgPayment) {
        Payment payment = paymentRepository.findByPgOrderId(pgPayment.pgOrderId())
            .orElseThrow(() -> new EntityNotFoundException("paymentKey 에 해당하는 결제가 없습니다."));

        if (!payment.getPaymentKey().value().equals(pgPayment.paymentKey())) { // <-- VO 내부 값 비교
            throw new IllegalStateException("PG 결제 키가 일치하지 않습니다.");
        }

        switch (pgPayment.status()) {
            case  COMPLETED -> {
                payment.validateApproval(
                    pgPayment.paymentKey(),
                    pgPayment.pgOrderId(),
                    pgPayment.amount()
                );

                if (payment.reconcileConfirmed(pgPayment.paymentKey())) {
                    Payment savedPayment = paymentRepository.save(payment);
                    savePaymentStatusOutboxIfAbsent(savedPayment); // <-- 정합화 경로의 중복 Outbox 저장 방지
                }

                return Optional.of(PaymentInfo.from(payment));
            }

            case FAILED , EXPIRED , CANCELLED -> {
                if (payment.reconcileFailed()) {
                    Payment savedPayment = paymentRepository.save(payment);
                    savePaymentStatusOutboxIfAbsent(savedPayment);
                }
            }

            case PENDING -> {
                if (payment.failIfConfirmingExpired(
                    LocalDateTime.now(),
                    paymentRecoveryProperties.maximumConfirmingDuration()
                )) {
                    Payment savedPayment = paymentRepository.save(payment);
                    savePaymentStatusOutboxIfAbsent(savedPayment);
                }
            }
        }

        return  Optional.empty();
    }

    // 추가 : 동일 결제 상태 Outbox 중복 생성 방지
    private void savePaymentStatusOutboxIfAbsent(Payment payment) {
        paymentStatusOutboxAppender.appendIfAbsent(payment);
    }

    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 결제입니다. paymentId = " + paymentId));
    }
}
