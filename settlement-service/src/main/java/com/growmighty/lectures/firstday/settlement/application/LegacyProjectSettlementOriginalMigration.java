package com.growmighty.lectures.firstday.settlement.application;

import com.growmighty.lectures.firstday.settlement.application.port.LegacyProjectSettlementOriginal;
import com.growmighty.lectures.firstday.settlement.application.port.LegacyProjectSettlementOriginalStore;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectSettlementTarget;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectSettlementTargetReader;
import com.growmighty.lectures.firstday.settlement.application.port.ResolvedProjectSettlementOriginal;
import com.growmighty.lectures.firstday.settlement.domain.SettlementFeePolicySnapshot;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final ProjectSettlementTargetReader projectSettlementTargetReader;

    public LegacyProjectSettlementOriginalMigrationResult migrate() {
        List<LegacyProjectSettlementOriginal> originals = List.copyOf(originalStore.findAll());
        List<LegacyProjectSettlementOriginal> originalsToBackfill = originals.stream()
                .filter(LegacyProjectSettlementOriginal::needsBackfill)
                .toList();
        List<String> failures = new ArrayList<>();
        Map<YearMonth, List<LegacyProjectSettlementOriginal>> originalsByMonth = groupBySettlementMonth(
                originalsToBackfill,
                failures
        );
        List<ResolvedProjectSettlementOriginal> resolved = new ArrayList<>();

        originalsByMonth.forEach((settlementMonth, monthlyOriginals) -> resolveMonthlyOriginals(
                settlementMonth,
                monthlyOriginals,
                resolved,
                failures
        ));

        if (!failures.isEmpty()) {
            throw new LegacyProjectSettlementOriginalMigrationException(failures);
        }

        originalStore.backfillAndEnforceRequiredOriginals(resolved);
        return new LegacyProjectSettlementOriginalMigrationResult(originalsToBackfill.size());
    }

    private static Map<YearMonth, List<LegacyProjectSettlementOriginal>> groupBySettlementMonth(
            List<LegacyProjectSettlementOriginal> originals,
            List<String> failures
    ) {
        Map<YearMonth, List<LegacyProjectSettlementOriginal>> grouped = new LinkedHashMap<>();
        for (LegacyProjectSettlementOriginal original : originals) {
            if (!hasValidIdentity(original) || original.scheduledDate() == null) {
                failures.add(describeFailure(original, "정산 대상 월 복원 불가"));
                continue;
            }
            YearMonth settlementMonth = YearMonth.from(original.scheduledDate().minusMonths(1));
            grouped.computeIfAbsent(settlementMonth, ignored -> new ArrayList<>()).add(original);
        }
        return grouped;
    }

    private void resolveMonthlyOriginals(
            YearMonth settlementMonth,
            List<LegacyProjectSettlementOriginal> monthlyOriginals,
            List<ResolvedProjectSettlementOriginal> resolved,
            List<String> failures
    ) {
        List<ProjectSettlementTarget> targets;
        try {
            targets = projectSettlementTargetReader.findSettlementTargets(settlementMonth);
        } catch (RuntimeException exception) {
            monthlyOriginals.forEach(original -> failures.add(
                    describeFailure(original, "Project 원본 조회 실패")
            ));
            return;
        }

        Map<Long, ProjectSettlementTarget> targetsByProjectId = indexTargets(targets);
        if (targetsByProjectId == null) {
            monthlyOriginals.forEach(original -> failures.add(
                    describeFailure(original, "Project 원본 응답 계약 위반")
            ));
            return;
        }

        for (LegacyProjectSettlementOriginal original : monthlyOriginals) {
            ProjectSettlementTarget target = targetsByProjectId.get(original.projectId());
            String failureReason = validateResolvedOriginal(original, target);
            if (failureReason != null) {
                failures.add(describeFailure(original, failureReason));
                continue;
            }
            resolved.add(new ResolvedProjectSettlementOriginal(
                    original.settlementId(),
                    original.projectId(),
                    original.creatorId(),
                    target.projectTitle(),
                    LEGACY_FEE_POLICY
            ));
        }
    }

    private static Map<Long, ProjectSettlementTarget> indexTargets(List<ProjectSettlementTarget> targets) {
        if (targets == null) {
            return null;
        }
        Map<Long, ProjectSettlementTarget> indexed = new HashMap<>();
        for (ProjectSettlementTarget target : targets) {
            if (target == null || indexed.putIfAbsent(target.projectId(), target) != null) {
                return null;
            }
        }
        return indexed;
    }

    private static String validateResolvedOriginal(
            LegacyProjectSettlementOriginal original,
            ProjectSettlementTarget target
    ) {
        if (target == null) {
            return "Project 원본 누락";
        }
        if (!original.creatorId().equals(target.creatorId())) {
            return "창작자 식별자 불일치";
        }
        if (original.projectTitle() != null
                && !original.projectTitle().isBlank()
                && !original.projectTitle().equals(target.projectTitle())) {
            return "기존 프로젝트 제목 불일치";
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
