package com.growmighty.lectures.firstday.settlement.application.query;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_REFUND_REQUEST_NOT_FOUND;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_RECONCILIATION_REVIEW_NOT_FOUND;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_SETTLEMENT_NOT_FOUND;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttempt;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectRefundRequested;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectRefundRequestedRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectOutcomeFactRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectPayoutInputRepository;
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
    private final PayoutObligationRepository payoutObligationRepository;
    private final ProjectRefundRequestedRepository refundRequestedRepository;
    private final ProjectOutcomeFactRepository outcomeRepository;
    private final ProjectPayoutInputRepository payoutInputRepository;
    private final AdminSettlementEntryRepository entryRepository;

    @Transactional(readOnly = true)
    public List<AdminSettlementEntry> findAll(AdminSettlementSort sort) {
        return entryRepository.findAll(sort);
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
                attempts
        );
    }

    @Transactional(readOnly = true)
    public AdminProjectRefundDetail findRefundDetail(Long refundRequestId) {
        ProjectRefundRequested request = refundRequestedRepository.findByRefundRequestId(refundRequestId)
                .orElseThrow(() -> new SettlementException(PROJECT_REFUND_REQUEST_NOT_FOUND));
        return new AdminProjectRefundDetail(
                request.refundRequestId(),
                request.projectId(),
                projectName(request.projectId()),
                request.reason(),
                refundStatus(request),
                request.occurredAt(),
                request.paymentResultAt(),
                request.payments().stream()
                        .map(payment -> new AdminProjectRefundDetail.Payment(
                                payment.orderId(), payment.pgOrderId(), request.failedOrderIds().contains(payment.orderId())
                        ))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public AdminProjectReconciliationReviewDetail findReconciliationReviewDetail(Long projectId) {
        ProjectOutcomeFact outcome = outcomeRepository.findAllByProjectIdIn(List.of(projectId)).stream()
                .filter(ProjectOutcomeFact::requiresPayout)
                .findFirst()
                .orElseThrow(() -> new SettlementException(PROJECT_RECONCILIATION_REVIEW_NOT_FOUND));
        List<AdminProjectReconciliationReviewDetail.Payment> payments = payoutInputRepository
                .findCompletedPaymentsByProjectId(projectId).stream()
                .filter(payment -> payment.reconciliationStatus() == OrderPaymentFact.ReconciliationStatus.REVIEW_REQUIRED)
                .map(payment -> new AdminProjectReconciliationReviewDetail.Payment(
                        payment.orderId(), payment.pgOrderId(), payment.reconciliationStatus()
                ))
                .toList();
        if (payments.isEmpty()) {
            throw new SettlementException(PROJECT_RECONCILIATION_REVIEW_NOT_FOUND);
        }
        return new AdminProjectReconciliationReviewDetail(projectId, outcome.projectName(), payments);
    }

    private PayoutObligation requiredObligation(Long settlementId) {
        return payoutObligationRepository.findBySettlementId(settlementId)
                .orElseThrow(() -> new SettlementException(PROJECT_SETTLEMENT_NOT_FOUND));
    }

    private String projectName(Long projectId) {
        return outcomeRepository.findAllByProjectIdIn(List.of(projectId)).stream()
                .findFirst()
                .map(ProjectOutcomeFact::projectName)
                .orElseThrow(() -> new IllegalStateException("프로젝트 결과 사실을 찾을 수 없습니다."));
    }

    private static AdminSettlementEntry.RefundStatus refundStatus(ProjectRefundRequested request) {
        return AdminSettlementEntry.RefundStatus.of(request.published(), request.paymentResultStatus());
    }

    private static LocalDateTime completedAt(PayoutObligation payoutObligation) {
        return payoutObligation.successfulAttempt()
                .map(PayoutAttempt::completedAt)
                .orElse(null);
    }

}
