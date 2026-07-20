package com.growmighty.lectures.firstday.project.reward.presentation;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.request.RewardCreateRequest;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.request.RewardUpdateRequest;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.response.RewardResponse;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    /** 공개 전: name/description/price/totalQuantity 자유 수정. 공개 후: increaseQuantity(추가량)만 허용. */
    @PatchMapping("/api/v1/rewards/{rewardId}")
    public ApiResponse<RewardResponse> update(@PathVariable Long rewardId, @Valid @RequestBody RewardUpdateRequest request) {
        return ApiResponse.ok(rewardService.update(rewardId, request));
    }

    /** 공개 전: 하드 삭제. 공개 후: 비활성화(판매 종료)만 수행하고 레코드는 보존. */
    @DeleteMapping("/api/v1/rewards/{rewardId}")
    public ApiResponse<Void> delete(@PathVariable Long rewardId) {
        rewardService.delete(rewardId);
        return ApiResponse.ok(null);
    }
}
