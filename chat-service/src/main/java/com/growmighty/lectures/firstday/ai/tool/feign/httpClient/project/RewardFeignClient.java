package com.growmighty.lectures.firstday.ai.tool.feign.httpClient.project;

import com.growmighty.lectures.firstday.ai.tool.feign.httpClient.project.dto.RewardApiData;
import com.growmighty.lectures.firstday.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "project-service", contextId = "reward")
public interface RewardFeignClient {

    @GetMapping("/api/v1/projects/{projectId}/rewards")
    ApiResponse<List<RewardApiData>> getRewardByProject(@PathVariable Long projectId);

    @GetMapping("/api/v1/rewards/{rewardId}")
    ApiResponse<RewardApiData> getReward(@PathVariable Long rewardId);
}
