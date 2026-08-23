package com.growmighty.lectures.firstday.ai.tool.feign.httpClient.project;

import com.growmighty.lectures.firstday.ai.tool.feign.httpClient.project.dto.ProjectCategoryApiData;
import com.growmighty.lectures.firstday.ai.tool.feign.port.project.ProjectCategoryPort;
import com.growmighty.lectures.firstday.ai.tool.feign.port.project.dto.ProjectCategoryResult;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectCategoryHttpClient implements ProjectCategoryPort {

    private final ProjectCategoryFeignClient projectCategoryFeignClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Override
    public List<ProjectCategoryResult> findAllLeafCategories() {
        return circuitBreakerFactory.create("projectCategories").run(
            this::fetch,
            this::failHard
        );
    }

    private List<ProjectCategoryResult> fetch() {
        List<ProjectCategoryApiData> tree = projectCategoryFeignClient.findAll().data();
        return flatten(tree, null);
    }

    private List<ProjectCategoryResult> flatten(List<ProjectCategoryApiData> nodes, String parentPath) {
        List<ProjectCategoryResult> results = new ArrayList<>();
        for (ProjectCategoryApiData node : nodes) {
            String path = parentPath == null ? node.name() : parentPath + "/" + node.name();
            if (node.children() == null || node.children().isEmpty()) {
                results.add(new ProjectCategoryResult(node.id(),node.name(), path));
            } else {
                results.addAll(flatten(node.children(), path));
            }
        }
        return results;
    }

    private List<ProjectCategoryResult> failHard(Throwable cause) {
        log.warn("카테고리 목록 조회 실패. 원인={}", cause.toString());
        throw new ServiceUnavailableException("카테고리 목록을 조회할 수 없습니다.");
    }
}
