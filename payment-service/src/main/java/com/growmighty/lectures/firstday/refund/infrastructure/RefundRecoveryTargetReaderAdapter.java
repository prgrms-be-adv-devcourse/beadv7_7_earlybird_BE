package com.growmighty.lectures.firstday.refund.infrastructure;

import com.growmighty.lectures.firstday.refund.application.dto.RefundRecoveryTarget;
import com.growmighty.lectures.firstday.refund.application.port.RefundRecoveryTargetReader;
import com.growmighty.lectures.firstday.refund.domain.RefundStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class RefundRecoveryTargetReaderAdapter implements RefundRecoveryTargetReader {

    private final RefundJpaRepository  refundJpaRepository;

    @Override
    public List<RefundRecoveryTarget> findTimedOutRequestTargets(LocalDateTime cutoff, int limit) {
        return refundJpaRepository.findTimedOutRequestedTargets(
            RefundStatus.REQUESTED,
            cutoff,
            PageRequest.of(0, limit)
        );
    }
}
