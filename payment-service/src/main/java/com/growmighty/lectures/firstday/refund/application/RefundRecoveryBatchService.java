package com.growmighty.lectures.firstday.refund.application;

import com.growmighty.lectures.firstday.refund.application.dto.RefundRecoveryTarget;
import com.growmighty.lectures.firstday.refund.application.port.RefundRecoveryTargetReader;
import com.growmighty.lectures.firstday.refund.config.RefundRecoveryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundRecoveryBatchService {
    private final RefundRecoveryService refundRecoveryService;
    private final RefundRecoveryProperties refundRecoveryProperties;
    private final RefundRecoveryTargetReader refundRecoveryTargetReader;

    //시간 초과된 REQUESTED 환불을 순차적으로 정합화
    public void recoverTimedOutRefunds() {
        LocalDateTime cutoff = LocalDateTime.now().minus(refundRecoveryProperties.requestedTimeOut());

        List<RefundRecoveryTarget> targets = refundRecoveryTargetReader.findTimedOutRequestTargets(cutoff, refundRecoveryProperties.batchSize());

        for (RefundRecoveryTarget target : targets) {
            try {
                refundRecoveryService.recover(target);
            } catch (RuntimeException exception) {
                log.warn("결제 취소 상태 복구에 실패했습니다. refundId={}", target.refundId(), exception);
            }
        }
    }
}
