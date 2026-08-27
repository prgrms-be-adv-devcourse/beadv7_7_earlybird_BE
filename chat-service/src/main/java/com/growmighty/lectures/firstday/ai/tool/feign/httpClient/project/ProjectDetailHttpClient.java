package com.growmighty.lectures.firstday.ai.tool.feign.httpClient.project;

import com.growmighty.lectures.firstday.ai.tool.feign.httpClient.project.dto.ProjectDetailApiData;
import com.growmighty.lectures.firstday.ai.tool.feign.port.file.FileLookupPort;
import com.growmighty.lectures.firstday.ai.tool.feign.port.project.ProjectDetailPort;
import com.growmighty.lectures.firstday.ai.tool.feign.port.project.dto.ProjectDetailResult;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectDetailHttpClient implements ProjectDetailPort {

    private final ProjectDetailFeignClient projectDetailFeignClient;
    private final CircuitBreakerFactory circuitBreakerFactory;
    private final FileLookupPort fileLookupPort;

    @Override
    public ProjectDetailResult findById(Long projectId) {
        return circuitBreakerFactory.create("projectDetail").run(
            () -> fetch(projectId),
            cause -> failHard(projectId, cause)
        );
    }

    private ProjectDetailResult fetch(Long projectId) {
        ProjectDetailApiData data = projectDetailFeignClient.findById(projectId).data();
        return new ProjectDetailResult(
            data.projectId(),
            data.title(),
            data.categoryId(),
            data.summary(),
            data.description(),
            data.goalAmount(),
            data.fundedAmount(),
            data.startAt(),
            data.endAt(),
            ProjectStatusDisplay.toKorean(data.status()),
            data.closed(),
            data.thumbnailId() != null ? fileLookupPort.findThumbnailUrl(data.projectId(), data.thumbnailId()) : null
        );
    }

    private ProjectDetailResult failHard(Long projectId, Throwable cause) {
        log.warn("프로젝트 상세 조회 실패. projectId={}, 원인={}", projectId, cause.toString());
        throw new ServiceUnavailableException("프로젝트 상세 조회를 할 수 없습니다. projectId=" + projectId);
    }
}
