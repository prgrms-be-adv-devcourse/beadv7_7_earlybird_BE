package com.growmighty.lectures.firstday.settlement.infrastructure.client.project;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectSettlementTarget;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectSettlementTargetReader;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public final class ProjectSettlementTargetHttpReader implements ProjectSettlementTargetReader {

    static final String PROJECTS_PATH = "/internal/v1/projects";
    static final String SETTLEMENT_TARGET_STATUS = "SUCCEEDED";

    private final RestClient restClient;

    public ProjectSettlementTargetHttpReader(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "Project HTTP 클라이언트는 필수입니다.");
    }

    @Override
    public List<ProjectSettlementTarget> findSettlementTargets() {
        ProjectSettlementTargetsResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(PROJECTS_PATH)
                            .queryParam("status", SETTLEMENT_TARGET_STATUS)
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
            List<ProjectSettlementTarget> targets = response.data().stream()
                    .map(ProjectSettlementTargetHttpReader::toSettlementTarget)
                    .toList();
            Set<Long> projectIds = new HashSet<>();
            if (targets.stream().anyMatch(target -> !projectIds.add(target.projectId()))) {
                throw new SettlementException(PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE);
            }
            return targets;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new SettlementException(PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE, exception);
        }
    }

    private static ProjectSettlementTarget toSettlementTarget(ProjectSettlementTargetResponse target) {
        if (target == null || !SETTLEMENT_TARGET_STATUS.equals(target.status())) {
            throw new IllegalArgumentException("성공 프로젝트 응답 계약을 위반했습니다.");
        }
        return new ProjectSettlementTarget(target.projectId(), target.creatorId());
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
