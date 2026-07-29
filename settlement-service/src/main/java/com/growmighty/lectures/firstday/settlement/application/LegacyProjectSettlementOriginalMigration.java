package com.growmighty.lectures.firstday.settlement.application;

import com.growmighty.lectures.firstday.settlement.application.port.LegacyProjectSettlementOriginal;
import com.growmighty.lectures.firstday.settlement.application.port.LegacyProjectSettlementOriginalStore;
import com.growmighty.lectures.firstday.settlement.application.port.ResolvedProjectSettlementOriginal;
import com.growmighty.lectures.firstday.settlement.domain.SettlementFeePolicySnapshot;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LegacyProjectSettlementOriginalMigration {

    private static final SettlementFeePolicySnapshot LEGACY_FEE_POLICY = SettlementFeePolicySnapshot.of(
            new BigDecimal("0.04"),
            new BigDecimal("0.04"),
            new BigDecimal("0.10")
    );

    private final LegacyProjectSettlementOriginalStore originalStore;

    public LegacyProjectSettlementOriginalMigrationResult migrate() {
        List<LegacyProjectSettlementOriginal> originals = List.copyOf(originalStore.findAll());
        List<LegacyProjectSettlementOriginal> originalsToBackfill = originals.stream()
                .filter(LegacyProjectSettlementOriginal::needsBackfill)
                .toList();
        List<String> failures = new ArrayList<>();
        List<ResolvedProjectSettlementOriginal> resolved = new ArrayList<>();
        for (LegacyProjectSettlementOriginal original : originalsToBackfill) {
            String failureReason = validateOriginal(original);
            if (failureReason != null) {
                failures.add(describeFailure(original, failureReason));
                continue;
            }
            resolved.add(new ResolvedProjectSettlementOriginal(
                    original.settlementId(),
                    original.projectId(),
                    original.creatorId(),
                    LEGACY_FEE_POLICY
            ));
        }

        if (!failures.isEmpty()) {
            throw new LegacyProjectSettlementOriginalMigrationException(failures);
        }

        originalStore.backfillAndEnforceRequiredOriginals(resolved);
        return new LegacyProjectSettlementOriginalMigrationResult(originalsToBackfill.size());
    }

    private static String validateOriginal(LegacyProjectSettlementOriginal original) {
        if (!hasValidIdentity(original)) {
            return "프로젝트 정산 식별자 복원 불가";
        }
        if (!matchesLegacyRate(
                original.paymentAndSettlementAgencyFeeRate(),
                LEGACY_FEE_POLICY.paymentAndSettlementAgencyFeeRate()
        ) || !matchesLegacyRate(
                original.platformFeeRate(),
                LEGACY_FEE_POLICY.platformFeeRate()
        ) || !matchesLegacyRate(original.vatRate(), LEGACY_FEE_POLICY.vatRate())) {
            return "기존 수수료 정책 불일치";
        }
        return null;
    }

    private static boolean matchesLegacyRate(BigDecimal existing, BigDecimal expected) {
        return existing == null || existing.compareTo(expected) == 0;
    }

    private static boolean hasValidIdentity(LegacyProjectSettlementOriginal original) {
        return original != null
                && original.settlementId() != null && original.settlementId() > 0
                && original.projectId() != null && original.projectId() > 0
                && original.creatorId() != null && original.creatorId() > 0;
    }

    private static String describeFailure(LegacyProjectSettlementOriginal original, String reason) {
        if (original == null) {
            return "settlementId=null, projectId=null, reason=" + reason;
        }
        return "settlementId=" + original.settlementId()
                + ", projectId=" + original.projectId()
                + ", reason=" + reason;
    }
}
