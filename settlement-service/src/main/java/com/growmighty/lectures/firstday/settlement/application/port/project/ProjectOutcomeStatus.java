// TODO(settlement-plan): Reuse these final states for Project facts and remove HTTP-response-specific conversion.
package com.growmighty.lectures.firstday.settlement.application.port.project;

public enum ProjectOutcomeStatus {
    SUCCEEDED,
    FAILED,
    CANCELLED
}
