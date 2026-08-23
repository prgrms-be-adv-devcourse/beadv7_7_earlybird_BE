package com.growmighty.lectures.firstday.ai.tool.feign.httpClient.project;

import com.growmighty.lectures.firstday.ai.tool.feign.httpClient.project.dto.ProjectCategoryApiData;
import com.growmighty.lectures.firstday.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "project-service", contextId = "projectCategory")
public interface ProjectCategoryFeignClient {

    @GetMapping("/api/v1/project-categories")
    ApiResponse<List<ProjectCategoryApiData>> findAll();
}
