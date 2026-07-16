package com.growmighty.lectures.firstday.project.reward.presentation;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.request.StockChangeRequest;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 간 내부 API — order-service가 후원 생성/취소 시 호출한다.
 * /api/v1 프리픽스를 쓰지 않는다: Gateway를 거치지 않는 내부망 전용 경로이며,
 * order-service의 RewardFeignClient와 경로가 정확히 일치해야 하는 런타임 계약이다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/rewards")
public class RewardInternalController {

    private final RewardService rewardService;

    @PostMapping("/{rewardId}/decrease-stock")
    public ApiResponse<Void> decreaseStock(@PathVariable Long rewardId, @Valid @RequestBody StockChangeRequest request) {
        rewardService.decreaseStock(rewardId, request.quantity());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{rewardId}/restore-stock")
    public ApiResponse<Void> restoreStock(@PathVariable Long rewardId, @Valid @RequestBody StockChangeRequest request) {
        rewardService.restoreStock(rewardId, request.quantity());
        return ApiResponse.ok(null);
    }
}
