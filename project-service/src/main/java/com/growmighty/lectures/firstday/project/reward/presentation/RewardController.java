package com.growmighty.lectures.firstday.project.reward.presentation;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.request.RewardCreateRequest;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.response.RewardResponse;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class RewardController {

    private final RewardService rewardService;

    @PostMapping("/api/v1/projects/{projectId}/rewards")
    public ApiResponse<RewardResponse> register(@PathVariable Long projectId,
                                                @Valid @RequestBody RewardCreateRequest request) {
        return ApiResponse.ok(rewardService.register(projectId, request));
    }

    @GetMapping("/api/v1/projects/{projectId}/rewards")
    public ApiResponse<List<RewardResponse>> getRewardsByProject(@PathVariable Long projectId) {
        return ApiResponse.ok(rewardService.getRewardsByProject(projectId));
    }

    @GetMapping("/api/v1/rewards/{rewardId}")
    public ApiResponse<RewardResponse> getReward(@PathVariable Long rewardId) {
        return ApiResponse.ok(rewardService.getReward(rewardId));
    }
}
