// TODO(settlement-plan): Reuse shared settlement query mapping while enforcing creator ownership in one place.
package com.growmighty.lectures.firstday.settlement.application.query;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_SETTLEMENT_NOT_FOUND;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.SETTLEMENT_DATA_INCONSISTENT;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.repository.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligationStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectSettlementRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreatorProjectSettlementQueryService {

    private final ProjectSettlementRepository projectSettlementRepository;
    private final PayoutObligationRepository payoutObligationRepository;

    @Transactional(readOnly = true)
    public List<CreatorProjectSettlementSummary> findAll(Long creatorId) {
        return projectSettlementRepository.findAllByCreatorIdOrderByConfirmedAtDescIdDesc(creatorId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public CreatorProjectSettlementDetail findDetail(Long creatorId, Long settlementId) {
        ProjectSettlement settlement = projectSettlementRepository.findById(settlementId)
                .filter(candidate -> candidate.creatorId().equals(creatorId))
                .orElseThrow(() -> new SettlementException(PROJECT_SETTLEMENT_NOT_FOUND));
        PayoutObligation obligation = payoutObligationRepository.findBySettlementId(settlement.id())
                .orElseThrow(() -> new SettlementException(SETTLEMENT_DATA_INCONSISTENT));
        requireConsistent(settlement, obligation);

        return new CreatorProjectSettlementDetail(
                settlement.id(),
                settlement.projectId(),
                settlement.confirmedAt(),
                settlement.feePolicySnapshot(),
                settlement.breakdown(),
                obligation.status(),
                obligation.scheduledDate(),
                completedAt(obligation),
                settlement.bankCode(),
                settlement.maskedAccountNumber()
        );
    }

    private CreatorProjectSettlementSummary toSummary(ProjectSettlement settlement) {
        PayoutObligation obligation = payoutObligationRepository.findBySettlementId(settlement.id())
                .orElseThrow(() -> new SettlementException(SETTLEMENT_DATA_INCONSISTENT));
        requireConsistent(settlement, obligation);

        return new CreatorProjectSettlementSummary(
                settlement.id(),
                settlement.projectId(),
                settlement.baseAmount(),
                settlement.creatorPayoutAmount(),
                obligation.status(),
                settlement.confirmedAt(),
                obligation.scheduledDate(),
                completedAt(obligation)
        );
    }

    private static LocalDateTime completedAt(PayoutObligation obligation) {
        LocalDateTime completedAt = obligation.successfulAttempt()
                .map(attempt -> attempt.completedAt())
                .orElse(null);
        if ((obligation.status() == PayoutObligationStatus.COMPLETED) != (completedAt != null)) {
            throw new SettlementException(SETTLEMENT_DATA_INCONSISTENT);
        }
        return completedAt;
    }

    private static void requireConsistent(ProjectSettlement settlement, PayoutObligation obligation) {
        if (!settlement.id().equals(obligation.settlementId())
                || !settlement.creatorId().equals(obligation.creatorId())
                || !settlement.creatorPayoutAmount().equals(obligation.amount())) {
            throw new SettlementException(SETTLEMENT_DATA_INCONSISTENT);
        }
    }
}
