package com.growmighty.lectures.firstday.settlement.application;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_SETTLEMENT_NOT_FOUND;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.SETTLEMENT_DATA_INCONSISTENT;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.domain.PayoutAttempt;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligationStatus;
import com.growmighty.lectures.firstday.settlement.domain.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.ProjectSettlementRepository;
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
        PayoutObligation obligation = payoutObligationRepository.findBySettlementId(settlement.id())
                .orElseThrow(() -> new SettlementException(SETTLEMENT_DATA_INCONSISTENT));
        requireConsistent(settlement, obligation);
        List<PayoutAttempt> attempts = obligation.attempts().stream()
                .sorted(Comparator.comparingInt(PayoutAttempt::sequence))
                .toList();

        return new AdminProjectSettlementDetail(
                settlement.id(),
                settlement.projectId(),
                settlement.projectTitle(),
                settlement.creatorId(),
                settlement.confirmedAt(),
                settlement.feePolicySnapshot(),
                settlement.breakdown(),
                obligation.id(),
                obligation.status(),
                obligation.scheduledDate(),
                completedAt(obligation),
                settlement.destinationSnapshot().tossSellerId(),
                settlement.destinationSnapshot().bankCode(),
                settlement.destinationSnapshot().maskedAccountNumber(),
                attempts
        );
    }

    private AdminProjectSettlementSummary toSummary(ProjectSettlement settlement) {
        PayoutObligation obligation = payoutObligationRepository.findBySettlementId(settlement.id())
                .orElseThrow(() -> new SettlementException(SETTLEMENT_DATA_INCONSISTENT));
        requireConsistent(settlement, obligation);

        return new AdminProjectSettlementSummary(
                settlement.id(),
                settlement.projectId(),
                settlement.projectTitle(),
                settlement.creatorId(),
                settlement.breakdown().baseAmount(),
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
