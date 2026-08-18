package com.growmighty.lectures.firstday.settlement.application.query;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_REFUND_REQUEST_NOT_FOUND;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_SETTLEMENT_NOT_FOUND;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttempt;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectRefundRequested;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectRefundRequestedRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectSettlementRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminProjectSettlementQueryService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ProjectSettlementRepository projectSettlementRepository;
    private final PayoutObligationRepository payoutObligationRepository;
    private final ProjectRefundRequestedRepository refundRequestedRepository;

    @Transactional(readOnly = true)
    public List<AdminSettlementEntry> findAll() {
        List<ProjectSettlement> settlements = projectSettlementRepository.findAllByOrderByConfirmedAtDescIdDesc();
        Map<Long, PayoutObligation> obligations = obligationsBySettlementId(settlements);
        return Stream.concat(
                        settlements.stream().map(settlement -> toPayoutEntry(
                                settlement,
                                requiredObligation(obligations, settlement.id())
                        )),
                        refundRequestedRepository.findAllByOrderByOccurredAtDescProjectIdDesc().stream()
                                .map(this::toRefundEntry)
                )
                .sorted(Comparator.comparing(AdminSettlementEntry::sortAt).reversed()
                        .thenComparing(AdminSettlementEntry::type)
                        .thenComparing(AdminSettlementEntry::sortId, Comparator.reverseOrder()))
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

    @Transactional(readOnly = true)
    public AdminProjectRefundDetail findRefundDetail(Long projectId) {
        ProjectRefundRequested request = refundRequestedRepository.findByProjectId(projectId)
                .orElseThrow(() -> new SettlementException(PROJECT_REFUND_REQUEST_NOT_FOUND));
        return new AdminProjectRefundDetail(
                request.projectId(),
                request.reason(),
                publishStatus(request),
                request.occurredAt(),
                request.publishedAt(),
                processingStatus(request),
                request.paymentResultAt(),
                request.payments().stream()
                        .map(payment -> new AdminProjectRefundDetail.Payment(payment.orderId(), payment.pgOrderId()))
                        .toList()
        );
    }

    private AdminSettlementEntry toPayoutEntry(ProjectSettlement settlement, PayoutObligation payoutObligation) {
        return new AdminSettlementEntry(
                AdminSettlementEntry.Type.PAYOUT,
                settlement.projectId(),
                atSeoul(settlement.confirmedAt()),
                settlement.id(),
                new AdminSettlementEntry.Payout(
                        settlement.id(),
                        settlement.creatorId(),
                        settlement.baseAmount(),
                        settlement.creatorPayoutAmount(),
                        payoutObligation.status(),
                        settlement.confirmedAt(),
                        payoutObligation.scheduledDate()
                ),
                null
        );
    }

    private AdminSettlementEntry toRefundEntry(ProjectRefundRequested request) {
        return new AdminSettlementEntry(
                AdminSettlementEntry.Type.REFUND,
                request.projectId(),
                request.occurredAt(),
                request.projectId(),
                null,
                new AdminSettlementEntry.Refund(
                        request.reason(),
                        publishStatus(request),
                        request.occurredAt(),
                        request.publishedAt(),
                        processingStatus(request),
                        request.paymentResultAt(),
                        request.payments().size()
                )
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

    private static AdminSettlementEntry.RefundPublishStatus publishStatus(ProjectRefundRequested request) {
        return request.published()
                ? AdminSettlementEntry.RefundPublishStatus.PUBLISHED
                : AdminSettlementEntry.RefundPublishStatus.REQUESTED;
    }

    private static AdminSettlementEntry.RefundProcessingStatus processingStatus(ProjectRefundRequested request) {
        if (request.paymentResultStatus() == null) {
            return AdminSettlementEntry.RefundProcessingStatus.AWAITING_RESULT;
        }
        return "COMPLETED".equals(request.paymentResultStatus())
                ? AdminSettlementEntry.RefundProcessingStatus.COMPLETED
                : AdminSettlementEntry.RefundProcessingStatus.ACTION_REQUIRED;
    }

    private static LocalDateTime completedAt(PayoutObligation payoutObligation) {
        return payoutObligation.successfulAttempt()
                .map(PayoutAttempt::completedAt)
                .orElse(null);
    }

    private static Instant atSeoul(LocalDateTime localDateTime) {
        return localDateTime.atZone(SEOUL).toInstant();
    }
}
