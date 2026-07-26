package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import com.growmighty.lectures.firstday.settlement.application.port.ProjectSettlementTarget;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectSettlementTargetReader;
import java.time.YearMonth;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "settlement.external-data.mode",
        havingValue = "dummy",
        matchIfMissing = true
)
public class DummyProjectSettlementTargetReader implements ProjectSettlementTargetReader {

    static final long DUMMY_PROJECT_ID = 9_000_001L;
    static final long DUMMY_CREATOR_ID = 9_000_001L;

    @Override
    public List<ProjectSettlementTarget> findSettlementTargets(YearMonth settlementMonth) {
        return List.of(new ProjectSettlementTarget(DUMMY_PROJECT_ID, DUMMY_CREATOR_ID));
    }
}
