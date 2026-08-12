// TODO(settlement-plan): Reuse shared settlement query mapping while enforcing creator ownership in one place.
package com.growmighty.lectures.firstday.settlement.application.query;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_SETTLEMENT_NOT_FOUND;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
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
        return new CreatorProjectSettlementDetail(
                settlement.id(),
                settlement.projectId(),
                settlement.confirmedAt(),
                settlement.paymentAndSettlementAgencyFeeRate(),
                settlement.platformFeeRate(),
                settlement.vatRate(),
                settlement.baseAmount(),
                settlement.paymentAndSettlementAgencyFeeAmount(),
                settlement.paymentAndSettlementAgencyFeeVatAmount(),
                settlement.platformFeeAmount(),
                settlement.platformFeeVatAmount(),
                settlement.otherDeductionAmount(),
                settlement.creatorPayoutAmount(),
                settlement.status(),
                settlement.scheduledDate(),
                completedAt(settlement),
                settlement.bankCode(),
                settlement.maskedAccountNumber()
        );
    }

    private CreatorProjectSettlementSummary toSummary(ProjectSettlement settlement) {
        return new CreatorProjectSettlementSummary(
                settlement.id(),
                settlement.projectId(),
                settlement.baseAmount(),
                settlement.creatorPayoutAmount(),
                settlement.status(),
                settlement.confirmedAt(),
                settlement.scheduledDate(),
                completedAt(settlement)
        );
    }

    private static LocalDateTime completedAt(ProjectSettlement settlement) {
        return settlement.successfulAttempt()
                .map(attempt -> attempt.completedAt())
                .orElse(null);
    }
}
