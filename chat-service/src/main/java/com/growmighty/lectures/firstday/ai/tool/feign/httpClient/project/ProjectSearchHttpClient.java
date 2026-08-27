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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectSearchHttpClient implements ProjectSearchPort {

    private static final int RECOMMENDATION_LIMIT = 5;

    // status를 명시적으로 지정한 조회(예 : 취소된 프로젝트 자체를 물어보는 경우)는 이 필터를 타지 않는다.
    private static final Set<String> DEFAULT_EXCLUDED_STATUSES = Set.of("CANCELLED","FAILED");

    private final ProjectSearchFeignClient projectSearchFeignClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    private final FileLookupPort fileLookupPort;

    @Override
    public ProjectSearchOutcome search(String keyword, Long categoryId, String status, String sort, Set<Long> excludeProjectIds) {
        return circuitBreakerFactory.create("projects").run(
            () -> fetch(keyword, categoryId, status, sort, excludeProjectIds),
            cause -> failHard(keyword, cause)
        );
    }

    private ProjectSearchOutcome failHard(String keyword, Throwable cause) {
        log.warn("프로젝트 검색 실패. keyword={}, 원인={}", keyword, cause.toString());
        throw new ServiceUnavailableException("프로젝트 검색을 처리할 수 없습니다. keyword=" + keyword);
    }

    private ProjectSearchOutcome fetch(String keyword, Long categoryId, String status, String sort, Set<Long> excludeProjectIds) {
        List<ProjectSearchApiData> data = projectSearchFeignClient.search(keyword,categoryId,status,sort).data();
        if (status == null) {
            data = data.stream()
                .filter(item -> !DEFAULT_EXCLUDED_STATUSES.contains(item.status()))
                .toList();
        }
        int totalCount = data.size();

        List<ProjectSearchApiData> unseen = excludeProjectIds.isEmpty()
            ? data
            : data.stream().filter(item -> !excludeProjectIds.contains(item.projectId())).toList();

        List<ProjectSearchApiData> candidates = unseen.stream()
            .limit(RECOMMENDATION_LIMIT)
            .toList();

        Map<String, Long> titleCounts = candidates.stream()
            .collect(Collectors.groupingBy(ProjectSearchApiData::title, Collectors.counting()));

        List<ProjectSearchResult> projects = candidates.stream()
            .map(item -> toResult(item, titleCounts.get(item.title()) > 1))
            .toList();


        boolean hasMore = unseen.size() > RECOMMENDATION_LIMIT;
        return new ProjectSearchOutcome(projects, hasMore, totalCount);
    }

    private ProjectSearchResult toResult(ProjectSearchApiData data, boolean disambiguateTitle) {
        String title = disambiguateTitle ? data.title() + " (#" + data.projectId() + ")" : data.title();
        return new ProjectSearchResult(
            data.projectId(),
            title,
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
