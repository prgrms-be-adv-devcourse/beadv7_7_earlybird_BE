package com.growmighty.lectures.firstday.settlement.application.port;

import java.util.List;

public interface ProjectSettlementTargetReader {

    List<ProjectSettlementTarget> findSettlementTargets();
}
