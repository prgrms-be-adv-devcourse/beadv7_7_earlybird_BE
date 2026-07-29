package com.growmighty.lectures.firstday.settlement.application;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PAYOUT_PROFILE_NOT_READY;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.SETTLEMENT_DATA_INCONSISTENT;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.PayoutDestinationSnapshot;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.ProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.domain.SettlementBreakdown;
import com.growmighty.lectures.firstday.settlement.domain.SettlementCalculationPolicy;
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
            PayoutObligation existingPayoutObligation = executePersistenceOperation(
                    () -> payoutObligationRepository.findBySettlementId(existingSettlement.id())
            )
                    .orElseThrow(() -> new SettlementException(SETTLEMENT_DATA_INCONSISTENT));
            return new ConfirmedProjectSettlement(
                    existingSettlement.projectId(),
                    existingSettlement.creatorId(),
                    existingSettlement.id(),
                    existingPayoutObligation.id(),
                    existingSettlement.creatorPayoutAmount(),
                    existingPayoutObligation.status(),
                    existingPayoutObligation.scheduledDate()
            );
        }

        CreatorPayoutProfile payoutProfile = executePersistenceOperation(
                () -> creatorPayoutProfileRepository.findByCreatorId(command.creatorId())
        )
                .orElseThrow(() -> new SettlementException(PAYOUT_PROFILE_NOT_READY));
        SettlementCalculationPolicy calculationPolicy = SettlementCalculationPolicy.current();
        SettlementBreakdown breakdown;
        try {
            breakdown = calculationPolicy.calculate(command.finalEffectivePaymentAmounts());
        } catch (IllegalArgumentException exception) {
            throw new SettlementException(FINAL_EFFECTIVE_PAYMENT_AMOUNTS_UNAVAILABLE, exception);
        }
        if (!payoutProfile.canReceivePayout()) {
            throw new SettlementException(PAYOUT_PROFILE_NOT_READY);
        }
        PayoutDestinationSnapshot destinationSnapshot = payoutProfile.snapshotDestination();
        ProjectSettlement settlementToSave = ProjectSettlement.confirm(
                command.projectId(),
                command.creatorId(),
                calculationPolicy.feePolicySnapshot(),
                breakdown,
                destinationSnapshot,
                command.confirmedAt()
        );
        ProjectSettlement settlement = executePersistenceOperation(
                () -> projectSettlementRepository.save(settlementToSave)
        );
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
