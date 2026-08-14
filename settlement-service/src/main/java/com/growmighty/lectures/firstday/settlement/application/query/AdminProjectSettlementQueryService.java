// TODO(settlement-plan): Consolidate repeated aggregate-to-query mapping and include new run or payout states without N+1 reads.
package com.growmighty.lectures.firstday.settlement.application.query;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_SETTLEMENT_NOT_FOUND;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttempt;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectSettlementRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminProjectSettlementQueryService {

    private final ProjectSettlementRepository projectSettlementRepository;
    private final PayoutObligationRepository payoutObligationRepository;

    @Transactional(readOnly = true)
    public List<AdminProjectSettlementSummary> findAll() {
        List<ProjectSettlement> settlements = projectSettlementRepository.findAllByOrderByConfirmedAtDescIdDesc();
        Map<Long, PayoutObligation> obligations = obligationsBySettlementId(settlements);
        return settlements.stream()
                .map(settlement -> toSummary(settlement, requiredObligation(obligations, settlement.id())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminProjectSettlementDetail findDetail(Long settlementId) {
        ProjectSettlement settlement = projectSettlementRepository.findById(settlementId)
                .orElseThrow(() -> new SettlementException(PROJECT_SETTLEMENT_NOT_FOUND));
        PayoutObligation payoutObligation = requiredObligation(settlement.id());
        List<PayoutAttempt> attempts = payoutObligation.attempts().stream()
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
                payoutObligation.status(),
                payoutObligation.scheduledDate(),
                completedAt(payoutObligation),
                payoutObligation.tossSellerId(),
                payoutObligation.bankCode(),
                payoutObligation.maskedAccountNumber(),
                attempts
        );
    }

    private AdminProjectSettlementSummary toSummary(ProjectSettlement settlement, PayoutObligation payoutObligation) {
        return new AdminProjectSettlementSummary(
                settlement.id(),
                settlement.projectId(),
                settlement.creatorId(),
                settlement.baseAmount(),
                settlement.creatorPayoutAmount(),
                payoutObligation.status(),
                settlement.confirmedAt(),
                payoutObligation.scheduledDate(),
                completedAt(payoutObligation)
        );
    }

    private Map<Long, PayoutObligation> obligationsBySettlementId(List<ProjectSettlement> settlements) {
        return payoutObligationRepository.findAllBySettlementIdIn(settlements.stream().map(ProjectSettlement::id).toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(PayoutObligation::settlementId, Function.identity()));
    }

    private PayoutObligation requiredObligation(Long settlementId) {
        return payoutObligationRepository.findBySettlementId(settlementId)
                .orElseThrow(() -> new SettlementException(PROJECT_SETTLEMENT_NOT_FOUND));
    }

    private static PayoutObligation requiredObligation(Map<Long, PayoutObligation> obligations, Long settlementId) {
        PayoutObligation payoutObligation = obligations.get(settlementId);
        if (payoutObligation == null) throw new SettlementException(PROJECT_SETTLEMENT_NOT_FOUND);
        return payoutObligation;
    }

    private static LocalDateTime completedAt(PayoutObligation payoutObligation) {
        return payoutObligation.successfulAttempt()
                .map(attempt -> attempt.completedAt())
                .orElse(null);
    }
}
