package com.growmighty.lectures.firstday.settlement.domain;

import java.util.List;
import java.util.Set;

public interface ProjectPaymentCancellationCommandRepository {

    List<ProjectPaymentCancellationCommand> saveAll(
            List<ProjectPaymentCancellationCommand> commands
    );

    List<ProjectPaymentCancellationCommand> findAllByProjectIdIn(Set<Long> projectIds);
}
