package com.growmighty.lectures.firstday.refund.application;

import com.growmighty.lectures.firstday.refund.domain.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class BulkRefundScheduler {

    private final RefundRepository refundRepository;
    private final RefundCancellationSagaOrchestrator refundCancellationSagaOrchestrator;

    @Scheduled(fixedDelayString = "${payment.bulk-refund.schedule-fixed-delay:2000}")
    public void cancelNextPlannedRefund() {
        // TODO: 다중 인스턴스 확장 시 조건부 UPDATE로 환불 작업을 원자적으로 선점해야 한다. // <--
        refundRepository.findNextCancelableRefundId(LocalDateTime.now())
            .ifPresent(refundId -> {
                try {
                    refundCancellationSagaOrchestrator.cancelPlannedRefund(refundId);
                } catch (RuntimeException exception) {
                    log.warn("일괄 환불 처리에 실패했습니다. refundId={}", refundId, exception);
                }
            });
    }
}
