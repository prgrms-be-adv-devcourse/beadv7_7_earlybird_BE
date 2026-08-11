// TODO(settlement-plan): Keep aggregate mapping local and support idempotent project and month queries.
package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter;

import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligationStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.entity.ProjectSettlementJpaEntity;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataProjectSettlementRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ProjectSettlementRepositoryAdapter implements ProjectSettlementRepository {

    private final SpringDataProjectSettlementRepository repository;
    private final PayoutObligationRepository payoutObligationRepository;

    @Override
    @Transactional
    public ProjectSettlement save(ProjectSettlement settlement) {
        if (settlement.id() == null) {
            ProjectSettlementJpaEntity saved = repository.saveAndFlush(
                    ProjectSettlementJpaEntity.fromDomain(settlement)
            );
            PayoutObligation payout = payoutObligationRepository.save(PayoutObligation.schedule(
                    saved.id(),
                    settlement.creatorId(),
                    settlement.creatorPayoutAmount(),
                    settlement.scheduledDate()
            ));
            return saved.toDomain(payout);
        }
        ProjectSettlementJpaEntity entity = repository.findById(settlement.id())
                .orElseThrow(() -> new IllegalStateException("저장된 프로젝트 정산이 존재하지 않습니다."));
        PayoutObligation current = payoutObligationRepository.findBySettlementId(settlement.id())
                .orElseThrow(() -> new IllegalStateException("저장된 지급 상태가 존재하지 않습니다."));
        PayoutObligation payout = payoutObligationRepository.save(PayoutObligation.restore(
                current.id(),
                settlement.id(),
                settlement.creatorId(),
                settlement.creatorPayoutAmount(),
                settlement.scheduledDate(),
                PayoutObligationStatus.fromPayoutStatus(settlement.status()),
                settlement.attempts(),
                settlement.successfulAttemptSequence(),
                settlement.version()
        ));
        return entity.toDomain(payout);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectSettlement> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectSettlement> findByProjectId(Long projectId) {
        return repository.findByProjectId(projectId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectSettlement> findAllByCreatorIdOrderByConfirmedAtDescIdDesc(Long creatorId) {
        return toDomains(repository.findAllByCreatorIdOrderByConfirmedAtDescIdDesc(creatorId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectSettlement> findAllByOrderByConfirmedAtDescIdDesc() {
        return toDomains(repository.findAllByOrderByConfirmedAtDescIdDesc());
    }

    private ProjectSettlement toDomain(ProjectSettlementJpaEntity entity) {
        // ponytail: bridge the legacy payout table until ProjectSettlement receives direct JPA mapping.
        PayoutObligation payout = payoutObligationRepository.findBySettlementId(entity.id())
                .orElseThrow(() -> new IllegalStateException("저장된 지급 상태가 존재하지 않습니다."));
        return entity.toDomain(payout);
    }

    private List<ProjectSettlement> toDomains(List<ProjectSettlementJpaEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        Map<Long, PayoutObligation> payoutsBySettlementId = payoutObligationRepository.findAllBySettlementIdIn(
                        entities.stream().map(ProjectSettlementJpaEntity::id).toList()
                ).stream()
                .collect(Collectors.toMap(PayoutObligation::settlementId, Function.identity()));
        return entities.stream()
                .map(entity -> entity.toDomain(requiredPayout(payoutsBySettlementId, entity.id())))
                .toList();
    }

    private static PayoutObligation requiredPayout(Map<Long, PayoutObligation> payoutsBySettlementId, Long settlementId) {
        PayoutObligation payout = payoutsBySettlementId.get(settlementId);
        if (payout == null) {
            throw new IllegalStateException("저장된 지급 상태가 존재하지 않습니다.");
        }
        return payout;
    }
}
