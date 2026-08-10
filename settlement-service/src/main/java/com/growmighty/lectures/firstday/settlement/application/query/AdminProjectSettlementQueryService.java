// TODO(settlement-plan): Consolidate repeated aggregate-to-query mapping and include new run or payout states without N+1 reads.
package com.growmighty.lectures.firstday.settlement.application.query;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_SETTLEMENT_NOT_FOUND;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttempt;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectSettlementRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminProjectSettlementQueryService {

    private final ProjectSettlementRepository projectSettlementRepository;

    @Transactional(readOnly = true)
    public List<AdminProjectSettlementSummary> findAll() {
        return projectSettlementRepository.findAllByOrderByConfirmedAtDescIdDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminProjectSettlementDetail findDetail(Long settlementId) {
        ProjectSettlement settlement = projectSettlementRepository.findById(settlementId)
                .orElseThrow(() -> new SettlementException(PROJECT_SETTLEMENT_NOT_FOUND));
        List<PayoutAttempt> attempts = settlement.attempts().stream()
                .sorted(Comparator.comparingInt(PayoutAttempt::sequence))
                .toList();

        return new AdminProjectSettlementDetail(
                settlement.id(),
                settlement.projectId(),
                settlement.creatorId(),
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
                settlement.tossSellerId(),
                settlement.bankCode(),
                settlement.maskedAccountNumber(),
                attempts
        );
    }

    private AdminProjectSettlementSummary toSummary(ProjectSettlement settlement) {
        return new AdminProjectSettlementSummary(
                settlement.id(),
                settlement.projectId(),
                settlement.creatorId(),
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
