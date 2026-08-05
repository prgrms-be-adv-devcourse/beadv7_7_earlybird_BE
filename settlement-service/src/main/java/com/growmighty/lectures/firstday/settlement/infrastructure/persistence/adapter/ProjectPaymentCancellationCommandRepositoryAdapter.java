package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter;

import com.growmighty.lectures.firstday.settlement.domain.model.ProjectPaymentCancellationCommand;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectPaymentCancellationCommandRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.entity.ProjectPaymentCancellationCommandJpaEntity;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataProjectPaymentCancellationCommandRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ProjectPaymentCancellationCommandRepositoryAdapter
        implements ProjectPaymentCancellationCommandRepository {

    private final SpringDataProjectPaymentCancellationCommandRepository repository;

    @Override
    @Transactional
    public List<ProjectPaymentCancellationCommand> saveAll(
            List<ProjectPaymentCancellationCommand> commands
    ) {
        List<ProjectPaymentCancellationCommandJpaEntity> entities = new ArrayList<>();
        for (ProjectPaymentCancellationCommand command : commands) {
            if (command.id() == null) {
                entities.add(ProjectPaymentCancellationCommandJpaEntity.fromDomain(command));
                continue;
            }
            ProjectPaymentCancellationCommandJpaEntity entity = repository.findById(command.id())
                    .orElseThrow(() -> new IllegalStateException("저장된 결제 취소 명령이 존재하지 않습니다."));
            if (!Objects.equals(entity.version(), command.version())) {
                throw new ObjectOptimisticLockingFailureException(
                        ProjectPaymentCancellationCommand.class,
                        command.id()
                );
            }
            entity.sync(command);
            entities.add(entity);
        }
        return repository.saveAllAndFlush(entities).stream()
                .map(ProjectPaymentCancellationCommandJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectPaymentCancellationCommand> findAllByProjectIdIn(Set<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return repository.findAllByProjectIdIn(projectIds).stream()
                .map(ProjectPaymentCancellationCommandJpaEntity::toDomain)
                .toList();
    }
}
