package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcome;
import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeReader;
import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeStatus;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "settlement.project-target.mode",
        havingValue = "dummy"
)
public class DummyProjectSettlementTargetReader implements ProjectOutcomeReader {

    static final long DUMMY_PROJECT_ID = 9_000_001L;
    static final long DUMMY_CREATOR_ID = 9_000_001L;

    @Override
    public List<ProjectOutcome> findProjectOutcomes() {
        return List.of(new ProjectOutcome(
                DUMMY_PROJECT_ID,
                DUMMY_CREATOR_ID,
                ProjectOutcomeStatus.SUCCEEDED
        ));
    }
}
