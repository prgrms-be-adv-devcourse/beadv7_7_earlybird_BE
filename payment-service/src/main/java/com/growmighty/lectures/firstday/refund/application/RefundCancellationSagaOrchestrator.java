package com.growmighty.lectures.firstday.refund.application;

import com.growmighty.lectures.firstday.refund.application.dto.RefundCancellationTarget;
import com.growmighty.lectures.firstday.refund.application.exception.RefundGatewayException;
import com.growmighty.lectures.firstday.refund.application.exception.RefundGatewayFailureType;
import com.growmighty.lectures.firstday.refund.application.port.RefundGateway;
import com.growmighty.lectures.firstday.refund.domain.RefundReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundCancellationSagaOrchestrator {
    private final RefundService refundService;
    private final RefundGateway refundGateway;

    // 환불 시작, Toss 취소, 내부 완료 상태 전이를 순서대로 처리
    public void cancel(Long paymentId, RefundReason reason) {
        RefundCancellationTarget target = refundService.startRefund(paymentId, reason);
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
            if (exception.getFailureType() == RefundGatewayFailureType.DEFINITIVE) {
                refundService.failRefund(target.refundId());
            }

            throw exception;
        }
    }
}
