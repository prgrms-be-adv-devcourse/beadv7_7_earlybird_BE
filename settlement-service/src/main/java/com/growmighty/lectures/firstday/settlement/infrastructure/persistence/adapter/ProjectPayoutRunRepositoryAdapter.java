package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter;

import com.growmighty.lectures.firstday.settlement.domain.model.ProjectPayoutRun;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectPayoutRunRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataProjectPayoutRunRepository;
import java.time.YearMonth;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ProjectPayoutRunRepositoryAdapter implements ProjectPayoutRunRepository {

    private final SpringDataProjectPayoutRunRepository repository;

    @Override
    @Transactional
    public ProjectPayoutRun save(ProjectPayoutRun run) {
        return repository.saveAndFlush(run);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectPayoutRun> findRunningByPayoutMonth(YearMonth payoutMonth) {
        return repository.findByActiveRunMonth(payoutMonth.toString());
    }
}
