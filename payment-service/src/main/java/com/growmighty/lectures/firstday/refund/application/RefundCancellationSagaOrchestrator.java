package com.growmighty.lectures.firstday.refund.application;

import com.growmighty.lectures.firstday.refund.application.dto.RefundCancellationTarget;
import com.growmighty.lectures.firstday.refund.application.exception.RefundGatewayException;
import com.growmighty.lectures.firstday.refund.application.exception.RefundGatewayFailureType;
import com.growmighty.lectures.firstday.refund.application.port.RefundGateway;
import com.growmighty.lectures.firstday.refund.domain.RefundReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundCancellationSagaOrchestrator {
    private final RefundService refundService;
    private final RefundGateway refundGateway;

    // 환불 시작, Toss 취소, 내부 완료 상태 전이를 순서대로 처리
    public void cancel(Long orderId, Long paymentId) {
        RefundCancellationTarget target = refundService.startRefund(orderId, paymentId, RefundReason.USER_CANCEL);
        processCancellation(target);
    }

    public void cancelPlannedRefund(Long refundId) {
        RefundCancellationTarget target = refundService.startPlannedRefund(refundId);
        processCancellation(target);
    }

    private void processCancellation(RefundCancellationTarget target) {
        try {
            refundGateway.refund(target.paymentKey(), target.reason(), target.cancelIdempotencyKey());

            refundService.completeRefund(target.refundId());
        } catch (RefundGatewayException exception) {
            try {
                if (exception.getFailureType() == RefundGatewayFailureType.DEFINITIVE) {
                    refundService.failRefund(target.refundId());
                } else {
                    refundService.scheduleRetry(target.refundId());
                }
            } catch (OptimisticLockingFailureException optimisticLockException) {
                log.info("다른 요청에서 환불 상태 전이가 이미 완료되었습니다. refundId={}", target.refundId());
            }
            throw exception;
        } catch (OptimisticLockingFailureException optimisticLockException) {
            log.info("다른 요청에서 환불 상태 전이가 이미 완료되었습니다. refundId={}", target.refundId());
        }
    }
}
