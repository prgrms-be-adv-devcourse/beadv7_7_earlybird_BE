package com.growmighty.lectures.firstday.ai.tool.feign.httpClient.project;

import com.growmighty.lectures.firstday.ai.tool.feign.httpClient.project.dto.ProjectSearchApiData;
import com.growmighty.lectures.firstday.ai.tool.feign.port.file.FileLookupPort;
import com.growmighty.lectures.firstday.ai.tool.feign.port.project.ProjectSearchPort;
import com.growmighty.lectures.firstday.ai.tool.feign.port.project.dto.ProjectSearchOutcome;
import com.growmighty.lectures.firstday.ai.tool.feign.port.project.dto.ProjectSearchResult;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectSearchHttpClient implements ProjectSearchPort {

    private static final int RECOMMENDATION_LIMIT = 5;

    private final ProjectSearchFeignClient projectSearchFeignClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    private final FileLookupPort fileLookupPort;

    @Override
    public ProjectSearchOutcome search(String keyword, Long categoryId, String status, String sort) {
        return circuitBreakerFactory.create("projects").run(
            () -> fetch(keyword, categoryId, status, sort),
            cause -> failHard(keyword, cause)
        );
    }

    private ProjectSearchOutcome failHard(String keyword, Throwable cause) {
        log.warn("프로젝트 검색 실패. keyword={}, 원인={}", keyword, cause.toString());
        throw new ServiceUnavailableException("프로젝트 검색을 처리할 수 없습니다. keyword=" + keyword);
    }

    private ProjectSearchOutcome fetch(String keyword, Long categoryId, String status, String sort) {
        List<ProjectSearchApiData> data = projectSearchFeignClient.search(keyword,categoryId,status,sort).data();
        List<ProjectSearchResult> projects = data.stream()
            .limit(RECOMMENDATION_LIMIT)
            .map(this::toResult)
            .toList();
        int totalCount = data.size();
        boolean hasMore = totalCount > RECOMMENDATION_LIMIT;
        return new ProjectSearchOutcome(projects, hasMore, totalCount);
    }

    private ProjectSearchResult toResult(ProjectSearchApiData data) {
        return new ProjectSearchResult(
            data.projectId(),
            data.title(),
            data.summary(),
            data.categoryId(),
            ProjectStatusDisplay.toKorean(data.status()),
            data.goalAmount(),
            data.fundedAmount(),
            data.endAt(),
            data.thumbnailId() != null ? fileLookupPort.findThumbnailUrl(data.projectId(), data.thumbnailId()) : null
        );
    }


}
