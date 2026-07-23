package com.growmighty.lectures.firstday.settlement.application;

import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.ProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.domain.SettlementBreakdown;
import com.growmighty.lectures.firstday.settlement.domain.SettlementCalculationPolicy;
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
        ProjectSettlement existingSettlement = projectSettlementRepository.findByProjectId(command.projectId())
                .orElse(null);
        if (existingSettlement != null) {
            PayoutObligation existingPayoutObligation = payoutObligationRepository
                    .findBySettlementId(existingSettlement.id())
                    .orElseThrow(() -> new IllegalStateException("프로젝트 정산의 지급 의무가 존재하지 않습니다."));
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

        CreatorPayoutProfile payoutProfile = creatorPayoutProfileRepository.findByCreatorId(command.creatorId())
                .orElseThrow(() -> new IllegalStateException("창작자 지급 프로필이 존재하지 않습니다."));
        SettlementBreakdown breakdown = SettlementCalculationPolicy.current()
                .calculate(command.finalEffectivePaymentAmounts());
        ProjectSettlement settlement = projectSettlementRepository.save(ProjectSettlement.confirm(
                command.projectId(),
                command.creatorId(),
                breakdown,
                payoutProfile.snapshotDestination(),
                command.confirmedAt()
        ));
        PayoutObligation payoutObligation = payoutObligationRepository.save(PayoutObligation.schedule(
                settlement.id(),
                settlement.creatorId(),
                settlement.creatorPayoutAmount(),
                command.scheduledDate()
        ));

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
}
