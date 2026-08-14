package com.growmighty.lectures.firstday.settlement.domain.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.ProjectPayoutRun;
import java.time.YearMonth;
import java.util.Optional;

public interface ProjectPayoutRunRepository {

    ProjectPayoutRun save(ProjectPayoutRun run);

    Optional<ProjectPayoutRun> findRunningByPayoutMonth(YearMonth payoutMonth);
}
