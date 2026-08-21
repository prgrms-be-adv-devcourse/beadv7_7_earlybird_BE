// TODO(settlement-plan): Keep calculation and confirmation transactional; move event consumption, reconciliation, and payout I/O outside.
package com.growmighty.lectures.firstday.settlement.application.settlement;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.ORDER_PAYMENT_INPUTS_UNAVAILABLE;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.SETTLEMENT_DATA_INCONSISTENT;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.repository.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.PayoutObligationRepository;
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
    private final CreatorPayoutProfileRepository creatorPayoutProfileRepository;
    private final PayoutObligationRepository payoutObligationRepository;

    @Transactional
    public ConfirmedProjectSettlement confirm(ConfirmProjectSettlementCommand command) {
        ProjectSettlement existingSettlement = executePersistenceOperation(
                () -> projectSettlementRepository.findByProjectId(command.projectId()).orElse(null)
        );
        if (existingSettlement != null) {
            return confirmedSettlement(existingSettlement, findPayoutObligation(existingSettlement.id()));
        }

        CreatorPayoutProfile payoutProfile = executePersistenceOperation(
                () -> creatorPayoutProfileRepository.findByCreatorId(command.creatorId())
        ).orElseGet(() -> executePersistenceOperation(
                () -> creatorPayoutProfileRepository.save(CreatorPayoutProfile.awaitingRegistration(command.creatorId()))
        ));
        ProjectSettlement settlementToSave;
        try {
            settlementToSave = ProjectSettlement.confirm(
                    command.projectId(),
                    command.creatorId(),
                    command.orderPaymentAmounts(),
                    command.confirmedAt()
            );
        } catch (IllegalArgumentException exception) {
            throw new SettlementException(ORDER_PAYMENT_INPUTS_UNAVAILABLE, exception);
        }
        ProjectSettlement settlement = executePersistenceOperation(
                () -> projectSettlementRepository.save(settlementToSave)
        );
        Optional<PayoutObligation> payoutObligation = payoutProfile.canReceivePayout()
                ? Optional.of(executePersistenceOperation(() -> payoutObligationRepository.save(
                        PayoutObligation.schedule(settlement, payoutProfile, command.scheduledDate())
                )))
                : Optional.empty();
        return confirmedSettlement(settlement, payoutObligation);
    }

    @Transactional(readOnly = true)
    public Optional<ConfirmedProjectSettlement> findConfirmedByProjectId(Long projectId) {
        return executePersistenceOperation(() -> projectSettlementRepository.findByProjectId(projectId))
                .map(settlement -> confirmedSettlement(settlement, findPayoutObligation(settlement.id())));
    }

    private ConfirmedProjectSettlement confirmedSettlement(
            ProjectSettlement settlement,
            Optional<PayoutObligation> payoutObligation
    ) {
        return new ConfirmedProjectSettlement(
                settlement.projectId(),
                settlement.creatorId(),
                settlement.id(),
                settlement.creatorPayoutAmount(),
                payoutObligation.map(PayoutObligation::status),
                payoutObligation.map(PayoutObligation::scheduledDate)
        );
    }

    private Optional<PayoutObligation> findPayoutObligation(Long settlementId) {
        return executePersistenceOperation(() -> payoutObligationRepository.findBySettlementId(settlementId));
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
