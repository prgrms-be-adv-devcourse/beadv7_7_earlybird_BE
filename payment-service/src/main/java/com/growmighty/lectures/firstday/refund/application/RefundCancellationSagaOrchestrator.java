package com.growmighty.lectures.firstday.refund.application;

import com.growmighty.lectures.firstday.refund.application.dto.RefundCancellationTarget;
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
    public void cancel(Long orderId, RefundReason reason) {
        RefundCancellationTarget target = refundService.startRefund(orderId, reason);

        refundGateway.refund(target.paymentKey(), target.reason());

        refundService.completeRefund(target.refundId());
    }
}
