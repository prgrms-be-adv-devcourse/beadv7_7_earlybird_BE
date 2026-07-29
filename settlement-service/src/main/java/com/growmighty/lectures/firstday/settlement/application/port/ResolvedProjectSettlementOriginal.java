package com.growmighty.lectures.firstday.settlement.application.port;

import com.growmighty.lectures.firstday.settlement.domain.SettlementFeePolicySnapshot;

public record ResolvedProjectSettlementOriginal(
        Long settlementId,
        Long projectId,
        Long creatorId,
        SettlementFeePolicySnapshot feePolicySnapshot
) {
}
