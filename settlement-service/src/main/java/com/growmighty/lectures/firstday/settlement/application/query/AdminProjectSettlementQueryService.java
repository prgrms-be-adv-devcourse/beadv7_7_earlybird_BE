package com.growmighty.lectures.firstday.settlement.application.query;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_REFUND_REQUEST_NOT_FOUND;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_SETTLEMENT_NOT_FOUND;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttempt;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectRefundRequested;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectRefundRequestedRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectOutcomeFactRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectSettlementRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
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
    private final ProjectOutcomeFactRepository outcomeRepository;

    @Transactional(readOnly = true)
    public List<AdminSettlementEntry> findAll(AdminSettlementSort sort) {
        List<ProjectSettlement> settlements = projectSettlementRepository.findAllByOrderByConfirmedAtDescIdDesc();
        List<ProjectRefundRequested> refundRequests = refundRequestedRepository.findAllByOrderByOccurredAtDescProjectIdDesc();
        Map<Long, PayoutObligation> obligations = obligationsBySettlementId(settlements);
        Map<Long, String> projectNames = projectNames(settlements, refundRequests);
        return Stream.concat(
                        settlements.stream().map(settlement -> toPayoutEntry(
                                settlement,
                                requiredObligation(obligations, settlement.id()),
                                requiredProjectName(projectNames, settlement.projectId())
                        )),
                        refundRequests.stream().map(request -> toRefundEntry(
                                request,
                                requiredProjectName(projectNames, request.projectId())
                        ))
                )
                .sorted(comparator(sort))
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
    public AdminProjectRefundDetail findRefundDetail(String refundRequestId) {
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

    private AdminSettlementEntry toPayoutEntry(
            ProjectSettlement settlement,
            PayoutObligation payoutObligation,
            String projectName
    ) {
        LocalDateTime payoutCompletedAt = completedAt(payoutObligation);
        return new AdminSettlementEntry(
                AdminSettlementEntry.Type.PAYOUT,
                settlement.projectId(),
                projectName,
                null,
                atSeoul(settlement.confirmedAt()),
                payoutCompletedAt == null ? null : atSeoul(payoutCompletedAt),
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

    private AdminSettlementEntry toRefundEntry(ProjectRefundRequested request, String projectName) {
        return new AdminSettlementEntry(
                AdminSettlementEntry.Type.REFUND,
                request.projectId(),
                projectName,
                request.refundRequestId(),
                request.occurredAt(),
                request.paymentResultAt(),
                null,
                new AdminSettlementEntry.Refund(
                        request.reason(),
                        request.occurredAt(),
                        refundStatus(request),
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

    private Map<Long, String> projectNames(
            List<ProjectSettlement> settlements,
            List<ProjectRefundRequested> refundRequests
    ) {
        HashSet<Long> projectIds = new HashSet<>();
        settlements.forEach(settlement -> projectIds.add(settlement.projectId()));
        refundRequests.forEach(request -> projectIds.add(request.projectId()));
        return outcomeRepository.findAllByProjectIdIn(projectIds).stream()
                .collect(java.util.stream.Collectors.toMap(ProjectOutcomeFact::projectId, ProjectOutcomeFact::projectName));
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

    private static String requiredProjectName(Map<Long, String> projectNames, Long projectId) {
        String projectName = projectNames.get(projectId);
        if (projectName == null) {
            throw new IllegalStateException("프로젝트 결과 사실을 찾을 수 없습니다.");
        }
        return projectName;
    }

    private String projectName(Long projectId) {
        return outcomeRepository.findAllByProjectIdIn(List.of(projectId)).stream()
                .findFirst()
                .map(ProjectOutcomeFact::projectName)
                .orElseThrow(() -> new IllegalStateException("프로젝트 결과 사실을 찾을 수 없습니다."));
    }

    private static Comparator<AdminSettlementEntry> comparator(AdminSettlementSort sort) {
        Comparator<AdminSettlementEntry> identity = Comparator.comparing(AdminSettlementEntry::type)
                .thenComparing(
                        entry -> entry.type() == AdminSettlementEntry.Type.PAYOUT ? entry.payout().settlementId() : null,
                        Comparator.nullsLast(Comparator.naturalOrder())
                )
                .thenComparing(
                        entry -> entry.type() == AdminSettlementEntry.Type.REFUND ? entry.refundRequestId() : null,
                        Comparator.nullsLast(Comparator.naturalOrder())
                );
        return switch (sort) {
            case NAME -> Comparator.comparing(AdminSettlementEntry::projectName).thenComparing(identity);
            case PUBLISHED_AT -> Comparator.comparing(AdminSettlementEntry::publishedAt).reversed().thenComparing(identity);
            case PROCESSED_AT -> Comparator.comparing(
                    AdminSettlementEntry::processedAt,
                    Comparator.nullsLast(Comparator.reverseOrder())
            ).thenComparing(identity);
        };
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

    private static AdminSettlementEntry.RefundStatus refundStatus(ProjectRefundRequested request) {
        if (!request.published()) {
            return AdminSettlementEntry.RefundStatus.REQUESTED;
        }
        if (request.paymentResultStatus() == null) {
            return AdminSettlementEntry.RefundStatus.PROCESSING;
        }
        return "COMPLETED".equals(request.paymentResultStatus())
                ? AdminSettlementEntry.RefundStatus.COMPLETED
                : AdminSettlementEntry.RefundStatus.ACTION_REQUIRED;
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
