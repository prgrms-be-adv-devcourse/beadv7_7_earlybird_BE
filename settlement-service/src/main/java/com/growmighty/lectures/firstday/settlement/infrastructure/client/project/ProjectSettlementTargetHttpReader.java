package com.growmighty.lectures.firstday.settlement.infrastructure.client.project;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectSettlementTarget;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectSettlementTargetReader;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public final class ProjectSettlementTargetHttpReader implements ProjectSettlementTargetReader {

    static final String SETTLEMENT_TARGETS_PATH = "/internal/projects/settlement-targets";

    private final RestClient restClient;

    public ProjectSettlementTargetHttpReader(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "Project HTTP 클라이언트는 필수입니다.");
    }

    @Override
    public List<ProjectSettlementTarget> findSettlementTargets(YearMonth settlementMonth) {
        ProjectSettlementTargetsResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(SETTLEMENT_TARGETS_PATH)
                            .queryParam("settlementMonth", settlementMonth)
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
                    .map(target -> new ProjectSettlementTarget(
                            target.projectId(),
                            target.projectTitle(),
                            target.creatorId()
                    ))
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

    private record ProjectSettlementTargetsResponse(
            boolean success,
            List<ProjectSettlementTargetResponse> data,
            Object error
    ) {
    }

    private record ProjectSettlementTargetResponse(
            Long projectId,
            String projectTitle,
            Long creatorId
    ) {
    }
}
