package com.growmighty.lectures.firstday.ai.tool.feign.httpClient.project;

import com.growmighty.lectures.firstday.ai.tool.feign.httpClient.project.dto.ProjectSearchApiData;
import com.growmighty.lectures.firstday.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "project-service", contextId = "projectSearch")
public interface ProjectSearchFeignClient {

    @GetMapping("/api/v1/projects")
    ApiResponse<List<ProjectSearchApiData>> search(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String sort
    );
}
