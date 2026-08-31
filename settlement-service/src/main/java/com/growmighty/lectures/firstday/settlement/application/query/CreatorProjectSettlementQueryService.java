// TODO(settlement-plan): Reuse shared settlement query mapping while enforcing creator ownership in one place.
package com.growmighty.lectures.firstday.settlement.application.query;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_SETTLEMENT_NOT_FOUND;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectSettlementRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreatorProjectSettlementQueryService {

    private final ProjectSettlementRepository projectSettlementRepository;
    private final PayoutObligationRepository payoutObligationRepository;
    private final CreatorPayoutProfileRepository creatorPayoutProfileRepository;

    @Transactional(readOnly = true)
    public List<CreatorProjectSettlementSummary> findAll(Long creatorId) {
        List<ProjectSettlement> settlements = projectSettlementRepository.findAllByCreatorIdOrderByConfirmedAtDescIdDesc(creatorId);
        Map<Long, PayoutObligation> obligations = obligationsBySettlementId(settlements);
        CreatorPayoutProfile payoutProfile = obligations.size() == settlements.size()
                ? null
                : requiredPayoutProfile(creatorId);
        return settlements.stream()
                .map(settlement -> toSummary(settlement, payoutFor(obligations.get(settlement.id()), payoutProfile)))
                .toList();
    }

    @Transactional(readOnly = true)
    public CreatorProjectSettlementDetail findDetail(Long creatorId, Long settlementId) {
        ProjectSettlement settlement = projectSettlementRepository.findById(settlementId)
                .filter(candidate -> candidate.creatorId().equals(creatorId))
                .orElseThrow(() -> new SettlementException(PROJECT_SETTLEMENT_NOT_FOUND));
        PayoutObligation payoutObligation = payoutObligationRepository.findBySettlementId(settlement.id()).orElse(null);
        CreatorPayoutProfile payoutProfile = payoutObligation == null ? requiredPayoutProfile(creatorId) : null;
        CreatorPayout payout = payoutFor(payoutObligation, payoutProfile);
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
                payout.status(),
                payout.scheduledDate(),
                payout.completedAt()
        );
    }

    private CreatorProjectSettlementSummary toSummary(ProjectSettlement settlement, CreatorPayout payout) {
        return new CreatorProjectSettlementSummary(
                settlement.id(),
                settlement.projectId(),
                settlement.baseAmount(),
                settlement.creatorPayoutAmount(),
                payout.status(),
                settlement.confirmedAt(),
                payout.scheduledDate(),
                payout.completedAt()
        );
    }

    private Map<Long, PayoutObligation> obligationsBySettlementId(List<ProjectSettlement> settlements) {
        return payoutObligationRepository.findAllBySettlementIdIn(settlements.stream().map(ProjectSettlement::id).toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(PayoutObligation::settlementId, Function.identity()));
    }

    private CreatorPayoutProfile requiredPayoutProfile(Long creatorId) {
        return creatorPayoutProfileRepository.findByCreatorId(creatorId)
                .orElseThrow(() -> new SettlementException(PROJECT_SETTLEMENT_NOT_FOUND));
    }

    private CreatorPayout payoutFor(PayoutObligation payoutObligation, CreatorPayoutProfile payoutProfile) {
        if (payoutObligation != null) {
            return new CreatorPayout(
                    CreatorSettlementStatus.from(payoutObligation.status()),
                    payoutObligation.scheduledDate(),
                    completedAt(payoutObligation)
            );
        }
        if (payoutProfile != null && payoutProfile.status() == CreatorPayoutStatus.REGISTRATION_PENDING) {
            return new CreatorPayout(CreatorSettlementStatus.REGISTRATION_PENDING, null, null);
        }
        throw new SettlementException(PROJECT_SETTLEMENT_NOT_FOUND);
    }

    private static LocalDateTime completedAt(PayoutObligation payoutObligation) {
        return payoutObligation.successfulAttempt()
                .map(attempt -> attempt.completedAt())
                .orElse(null);
    }

    private record CreatorPayout(
            CreatorSettlementStatus status,
            LocalDate scheduledDate,
            LocalDateTime completedAt
    ) {
    }
}
