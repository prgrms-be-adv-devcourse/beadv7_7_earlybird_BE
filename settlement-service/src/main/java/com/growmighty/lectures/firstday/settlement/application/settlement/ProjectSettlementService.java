// TODO(settlement-plan): Keep calculation and confirmation transactional; move event consumption, reconciliation, and payout I/O outside.
package com.growmighty.lectures.firstday.settlement.application.settlement;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.ORDER_PAYMENT_INPUTS_UNAVAILABLE;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PAYOUT_PROFILE_NOT_READY;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.SETTLEMENT_DATA_INCONSISTENT;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.repository.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.repository.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectSettlementRepository;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectSettlementService {

    private final ProjectSettlementRepository projectSettlementRepository;
    private final PayoutObligationRepository payoutObligationRepository;
    private final CreatorPayoutProfileRepository creatorPayoutProfileRepository;

    @Transactional
    public ConfirmedProjectSettlement confirm(ConfirmProjectSettlementCommand command) {
        ProjectSettlement existingSettlement = executePersistenceOperation(
                () -> projectSettlementRepository.findByProjectId(command.projectId()).orElse(null)
        );
        if (existingSettlement != null) {
            return confirmedSettlement(existingSettlement);
        }

        CreatorPayoutProfile payoutProfile = executePersistenceOperation(
                () -> creatorPayoutProfileRepository.findByCreatorId(command.creatorId())
        )
                .orElseThrow(() -> new SettlementException(PAYOUT_PROFILE_NOT_READY));
        if (!payoutProfile.canReceivePayout()) {
            throw new SettlementException(PAYOUT_PROFILE_NOT_READY);
        }
        ProjectSettlement settlementToSave;
        try {
            settlementToSave = ProjectSettlement.confirm(
                    command.projectId(),
                    command.creatorId(),
                    command.orderPaymentAmounts(),
                    payoutProfile,
                    command.confirmedAt()
            );
        } catch (IllegalArgumentException exception) {
            throw new SettlementException(ORDER_PAYMENT_INPUTS_UNAVAILABLE, exception);
        }
        ProjectSettlement settlement = executePersistenceOperation(
                () -> projectSettlementRepository.save(settlementToSave)
        );
        // ponytail: remove this compatibility write when payout execution moves onto ProjectSettlement.
        PayoutObligation payoutObligationToSave = PayoutObligation.schedule(
                settlement.id(),
                settlement.creatorId(),
                settlement.creatorPayoutAmount(),
                command.scheduledDate()
        );
        PayoutObligation payoutObligation = executePersistenceOperation(
                () -> payoutObligationRepository.save(payoutObligationToSave)
        );

        return new ConfirmedProjectSettlement(
                settlement.projectId(),
                settlement.creatorId(),
                settlement.id(),
                payoutObligation.id(),
                settlement.creatorPayoutAmount(),
                payoutObligation.status(),
                payoutObligation.scheduledDate()
        );
    }

    @Transactional(readOnly = true)
    public Optional<ConfirmedProjectSettlement> findConfirmedByProjectId(Long projectId) {
        return executePersistenceOperation(() -> projectSettlementRepository.findByProjectId(projectId))
                .map(this::confirmedSettlement);
    }

    private ConfirmedProjectSettlement confirmedSettlement(ProjectSettlement settlement) {
        PayoutObligation payoutObligation = executePersistenceOperation(
                () -> payoutObligationRepository.findBySettlementId(settlement.id())
        ).orElseThrow(() -> new SettlementException(SETTLEMENT_DATA_INCONSISTENT));
        return new ConfirmedProjectSettlement(
                settlement.projectId(),
                settlement.creatorId(),
                settlement.id(),
                payoutObligation.id(),
                settlement.creatorPayoutAmount(),
                payoutObligation.status(),
                payoutObligation.scheduledDate()
        );
    }

    private <T> T executePersistenceOperation(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (SettlementException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SettlementException(SETTLEMENT_DATA_INCONSISTENT, exception);
        }
    }
}
