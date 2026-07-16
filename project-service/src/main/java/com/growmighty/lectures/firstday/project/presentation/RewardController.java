package com.growmighty.lectures.firstday.project.presentation;

import com.growmighty.lectures.firstday.project.application.RewardService;
import com.growmighty.lectures.firstday.project.presentation.dto.ChangeStockRequest;
import com.growmighty.lectures.firstday.project.presentation.dto.RegisterRewardRequest;
import com.growmighty.lectures.firstday.project.presentation.dto.RewardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 리워드 API.
 * 재고 차감/복원은 order-service가 호출하는 서비스 간 내부 API다
 * (RewardFeignClient와 경로가 일치해야 한다 — 런타임 계약).
 */
@RestController
@RequiredArgsConstructor
public class RewardController {
    private final RewardService rewardService;

    @PostMapping("/projects/{projectId}/rewards")
    public RewardResponse register(@PathVariable Long projectId,
                                                @RequestBody RegisterRewardRequest request) {
        return RewardResponse.from(rewardService.register(request.toCommand(projectId)));
    }

    @GetMapping("/projects/{projectId}/rewards")
    public List<RewardResponse> getRewardsByProject(@PathVariable Long projectId) {
        return rewardService.getRewardsByProject(projectId).stream()
            .map(RewardResponse::from)
            .toList();
    }

    @GetMapping("/rewards/{rewardId}")
    public RewardResponse getReward(@PathVariable Long rewardId) {
        return RewardResponse.from(rewardService.getReward(rewardId));
    }

    @PostMapping("/internal/rewards/{rewardId}/decrease-stock")
    public Void decreaseStock(@PathVariable Long rewardId, @RequestBody ChangeStockRequest request) {
        rewardService.decreaseStock(rewardId, request.quantity());
        return null;
    }

    @PostMapping("/internal/rewards/{rewardId}/restore-stock")
    public Void restoreStock(@PathVariable Long rewardId, @RequestBody ChangeStockRequest request) {
        rewardService.restoreStock(rewardId, request.quantity());
        return null;
    }
}
