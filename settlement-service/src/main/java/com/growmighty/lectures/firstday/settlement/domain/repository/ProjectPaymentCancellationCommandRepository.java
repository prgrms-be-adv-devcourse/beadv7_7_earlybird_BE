// TODO(settlement-plan): Replace this per-order command repository with a project refund Outbox repository, then delete it.
package com.growmighty.lectures.firstday.settlement.domain.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.ProjectPaymentCancellationCommand;
import java.util.List;
import java.util.Set;

public interface ProjectPaymentCancellationCommandRepository {

    List<ProjectPaymentCancellationCommand> saveAll(
            List<ProjectPaymentCancellationCommand> commands
    );

    List<ProjectPaymentCancellationCommand> findAllByProjectIdIn(Set<Long> projectIds);
}
