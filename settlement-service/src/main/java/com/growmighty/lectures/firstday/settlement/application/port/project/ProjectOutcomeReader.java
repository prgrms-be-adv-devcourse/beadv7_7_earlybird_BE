// TODO(settlement-plan): Delete this synchronous Project port after ProjectEnded and ProjectCancelled consumers become authoritative.
package com.growmighty.lectures.firstday.settlement.application.port.project;

import java.util.List;

public interface ProjectOutcomeReader {

    List<ProjectOutcome> findProjectOutcomes();
}
