// TODO(settlement-plan): Delete this adapter after persisted ProjectEnded and ProjectCancelled facts become authoritative.
package com.growmighty.lectures.firstday.settlement.infrastructure.client.project;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcome;
import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeReader;
import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeStatus;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public final class ProjectSettlementTargetHttpReader implements ProjectOutcomeReader {

    static final String PROJECTS_PATH = "/internal/v1/projects";
    private static final List<ProjectOutcomeStatus> OUTCOME_STATUSES = List.of(
            ProjectOutcomeStatus.SUCCEEDED,
            ProjectOutcomeStatus.FAILED,
            ProjectOutcomeStatus.CANCELLED
    );

    private final RestClient restClient;

    public ProjectSettlementTargetHttpReader(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "Project HTTP 클라이언트는 필수입니다.");
    }

    @Override
    public List<ProjectOutcome> findProjectOutcomes() {
        List<ProjectOutcome> outcomes = new ArrayList<>();
        for (ProjectOutcomeStatus status : OUTCOME_STATUSES) {
            outcomes.addAll(findProjectOutcomes(status));
        }
        if (hasDuplicateProjectId(outcomes)) {
            throw new SettlementException(PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE);
        }
        return List.copyOf(outcomes);
    }

    private List<ProjectOutcome> findProjectOutcomes(ProjectOutcomeStatus expectedStatus) {
        ProjectSettlementTargetsResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(PROJECTS_PATH)
                            .queryParam("status", expectedStatus.name())
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ProjectSettlementTargetsResponse.class);
        } catch (RestClientException exception) {
            throw new SettlementException(PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE, exception);
        }

        if (response == null
                || !response.success()
                || response.data() == null
                || response.error() != null) {
            throw new SettlementException(PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE);
        }
        try {
            List<ProjectOutcome> outcomes = response.data().stream()
                    .map(target -> toProjectOutcome(target, expectedStatus))
                    .toList();
            if (hasDuplicateProjectId(outcomes)) {
                throw new SettlementException(PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE);
            }
            return outcomes;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new SettlementException(PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE, exception);
        }
    }

    private static ProjectOutcome toProjectOutcome(
            ProjectSettlementTargetResponse target,
            ProjectOutcomeStatus expectedStatus
    ) {
        if (target == null || !expectedStatus.name().equals(target.status())) {
            throw new IllegalArgumentException("Project 결과 상태 응답 계약을 위반했습니다.");
        }
        return new ProjectOutcome(
                target.projectId(),
                target.creatorId(),
                expectedStatus
        );
    }

    private static boolean hasDuplicateProjectId(List<ProjectOutcome> outcomes) {
        Set<Long> projectIds = new HashSet<>();
        return outcomes.stream().anyMatch(outcome -> !projectIds.add(outcome.projectId()));
    }

    private record ProjectSettlementTargetsResponse(
            boolean success,
            List<ProjectSettlementTargetResponse> data,
            Object error
    ) {
    }

    private record ProjectSettlementTargetResponse(
            Long projectId,
            Long creatorId,
            String status
    ) {
    }
}
