package com.growmighty.lectures.firstday.payment.application;


import com.growmighty.lectures.firstday.payment.config.PaymentRecoveryProperties;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRecoveryBatchService {
    private final PaymentRepository paymentRepository;
    private final PaymentRecoveryService paymentRecoveryService;
    private final PaymentRecoveryProperties paymentRecoveryProperties;

    public void recoverTimedOutPayments() {
        LocalDateTime cutoff = LocalDateTime.now().minus(paymentRecoveryProperties.confirmationTimeOut());

        List<Long> paymentIds = paymentRepository.findConfirmingPaymentIdsBefore(
            cutoff,
            paymentRecoveryProperties.batchSize()
        );

        /**
         * for문 선택 이유 : 결제처럼 민감한 외부 API 호출에서 안전하고 쉽게 디버깅이 가능하다.
         * 지금은 최대 100건만 처리하고, 3분 주기로 실행할 예정이므로 순차처리가 적합하다고 판단했음
         * 나중에 적체현상이 보이면 그때 서비스로 동시 최대 N건으로 병렬처리로 확장 예정
         */
        for (Long paymentId : paymentIds) {
            try {
                paymentRecoveryService.recover(paymentId);
            } catch (RuntimeException e) {
                log.warn("결제 승인 상태 복구에 실패했습니다. paymentId={}", paymentId, e);
            }
        }
    }

    /**
     * READY 만료는 외부 API 호출은 없지만, 결제 상태 변경과 Outbox 저장을 결제 별 독립 트랜잭션으로 처리함
     * 한건의 실패가 전체 만료 배치에 영향을 주지 않도록 순차 처리함
     */
    public void expireTimedOutPayments() {
        LocalDateTime cutoff = LocalDateTime.now().minus(paymentRecoveryProperties.readyTimeOut());

        List<Long> paymentIds = paymentRepository.findReadyPaymentIdsBefore(
            cutoff,
            paymentRecoveryProperties.batchSize());

        for (Long paymentId : paymentIds) {
            try {
                paymentRecoveryService.expireReadyPayment(paymentId);
            } catch (RuntimeException e) {
                log.warn("READY 결제 만료 처리에 실패했습니다. paymentId={}", paymentId, e);
            }
        }

    }
}
