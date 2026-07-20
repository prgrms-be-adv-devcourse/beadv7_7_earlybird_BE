package com.growmighty.lectures.firstday.project.reward.presentation;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.request.RewardQuantityDecreaseRequest;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.response.RewardResponse;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 전용 리워드 API.
 * TODO(팀): 관리자 권한 검증은 인증 도입 후 — Gateway 라우팅에 /admin 규칙 추가 필요
 * TODO(팀): JWT 도입만으로는 해결 안 됨 — 로그인 여부와 별개로 "이 사용자가 ADMIN role인가"를
 *           검증하는 role 체크 로직을 이 컨트롤러/서비스에 별도로 추가해야 한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/rewards")
public class RewardAdminController {

    private final RewardService rewardService;

    /** 공개(진행중) 리워드의 수량 축소 — 부득이한 축소만, 이미 판매된 수량 밑으로는 불가. 크리에이터는 늘리기만 가능. */
    @PatchMapping("/{rewardId}/quantity")
    public ApiResponse<RewardResponse> decreaseQuantity(@PathVariable Long rewardId,
                                                          @Valid @RequestBody RewardQuantityDecreaseRequest request) {
        return ApiResponse.ok(rewardService.decreaseQuantity(rewardId, request.amount()));
    }

    /** 공개(진행중) 리워드 비활성화 — 크리에이터는 이 권한이 없다, 관리자 전용. */
    @DeleteMapping("/{rewardId}")
    public ApiResponse<Void> deactivate(@PathVariable Long rewardId) {
        rewardService.deactivate(rewardId);
        return ApiResponse.ok(null);
    }
}
