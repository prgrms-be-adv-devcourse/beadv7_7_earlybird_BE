// TODO(settlement-plan): Reuse shared settlement query mapping while enforcing creator ownership in one place.
package com.growmighty.lectures.firstday.settlement.application.query;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_SETTLEMENT_NOT_FOUND;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectRefundRequested;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectOutcomeFactRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectPayoutInputRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectRefundRequestedRepository;
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
    private final ProjectOutcomeFactRepository outcomeRepository;
    private final ProjectPayoutInputRepository payoutInputRepository;
    private final ProjectRefundRequestedRepository refundRequestedRepository;

    @Transactional(readOnly = true)
    public List<CreatorProjectSettlementSummary> findAll(Long creatorId) {
        List<ProjectSettlement> settlements = projectSettlementRepository.findAllByCreatorIdOrderByConfirmedAtDescIdDesc(creatorId);
        Map<Long, PayoutObligation> obligations = obligationsBySettlementId(settlements);
        CreatorPayoutProfile payoutProfile = obligations.size() == settlements.size()
                ? null
                : requiredPayoutProfile(creatorId);
        Map<Long, ProjectSettlement> settlementsByProjectId = settlements.stream()
                .collect(java.util.stream.Collectors.toMap(ProjectSettlement::projectId, Function.identity()));
        List<ProjectOutcomeFact> outcomes = outcomeRepository.findAllByCreatorIdOrderByOccurredAtDescProjectIdDesc(creatorId);
        Map<Long, ProjectRefundRequested> refundsByProjectId = refundRequestedRepository.findAllByProjectIdIn(
                        outcomes.stream().map(ProjectOutcomeFact::projectId).toList()
                ).stream()
                .collect(java.util.stream.Collectors.toMap(ProjectRefundRequested::projectId, Function.identity()));
        return java.util.stream.Stream.concat(
                        settlements.stream()
                                .map(settlement -> toSummary(settlement, payoutFor(obligations.get(settlement.id()), payoutProfile))),
                        outcomes.stream()
                                .filter(outcome -> !settlementsByProjectId.containsKey(outcome.projectId()))
                                .map(outcome -> toSummary(outcome, refundsByProjectId.get(outcome.projectId())))
                )
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

    private CreatorProjectSettlementSummary toSummary(
            ProjectOutcomeFact outcome,
            ProjectRefundRequested refundRequest
    ) {
        return new CreatorProjectSettlementSummary(
                null,
                outcome.projectId(),
                null,
                null,
                outcome.requiresPayout()
                        ? payoutStatus(outcome)
                        : refundStatus(refundRequest),
                null,
                null,
                null
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
        if (payoutProfile != null) {
            return new CreatorPayout(CreatorSettlementStatus.from(payoutProfile.status()), null, null);
        }
        throw new SettlementException(PROJECT_SETTLEMENT_NOT_FOUND);
    }

    private CreatorSettlementStatus payoutStatus(ProjectOutcomeFact outcome) {
        return payoutInputRepository.findCompletedPaymentsByProjectId(outcome.projectId()).stream()
                .anyMatch(payment -> payment.reconciliationStatus() == OrderPaymentFact.ReconciliationStatus.REVIEW_REQUIRED)
                ? CreatorSettlementStatus.RECONCILIATION_REVIEW_REQUIRED
                : CreatorSettlementStatus.SETTLEMENT_PENDING;
    }

    private CreatorSettlementStatus refundStatus(ProjectRefundRequested refundRequest) {
        if (refundRequest == null) {
            return CreatorSettlementStatus.REFUND_PENDING;
        }
        if (!refundRequest.published()) {
            return CreatorSettlementStatus.REFUND_REQUESTED;
        }
        if (refundRequest.paymentResultStatus() == null) {
            return CreatorSettlementStatus.REFUND_PROCESSING;
        }
        return "COMPLETED".equals(refundRequest.paymentResultStatus())
                ? CreatorSettlementStatus.REFUND_COMPLETED
                : CreatorSettlementStatus.REFUND_ACTION_REQUIRED;
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
