package com.growmighty.lectures.firstday.refund.application.port;

import com.growmighty.lectures.firstday.refund.application.dto.RefundRecoveryTarget;

import java.time.LocalDateTime;
import java.util.List;

public interface RefundRecoveryTargetReader {

    List<RefundRecoveryTarget> findTimedOutRequestTargets(
        LocalDateTime cutoff,
        int limit
    );

}
