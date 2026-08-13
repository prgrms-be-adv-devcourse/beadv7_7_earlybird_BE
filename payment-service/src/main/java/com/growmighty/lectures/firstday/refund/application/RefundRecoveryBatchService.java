package com.growmighty.lectures.firstday.refund.application;

import com.growmighty.lectures.firstday.refund.config.RefundRecoveryProperties;
import com.growmighty.lectures.firstday.refund.domain.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundRecoveryBatchService {
    private final RefundRepository refundRepository;
    private final RefundRecoveryService refundRecoveryService;
    private final RefundRecoveryProperties refundRecoveryProperties;

    //시간 초과된 REQUESTED 환불을 순차적으로 정합화
    public void recoverTimedOutRefunds() {
        LocalDateTime cutoff = LocalDateTime.now().minus(refundRecoveryProperties.requestedTimeOut());

        List<Long> refundIds = refundRepository.findRecoveryTargetIds(cutoff, refundRecoveryProperties.batchSize());

        for (Long refundId : refundIds) {
            try {
                refundRecoveryService.recover(refundId);
            } catch (RuntimeException exception) {
                log.warn("환불 상태 복구에 실패했습니다. refundId={}", refundId, exception);
            }
        }
    }
}
