package com.growmighty.lectures.firstday.ai.tool.feign.httpClient.project;

import com.growmighty.lectures.firstday.ai.tool.feign.httpClient.project.dto.ProjectDetailApiData;
import com.growmighty.lectures.firstday.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "project-service", contextId = "projectDetail")
public interface ProjectDetailFeignClient {

    @GetMapping("/api/v1/projects/{projectId}")
    ApiResponse<ProjectDetailApiData> findById(@PathVariable Long projectId);
}
