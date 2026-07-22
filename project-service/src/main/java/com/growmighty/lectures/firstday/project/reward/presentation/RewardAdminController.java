package com.growmighty.lectures.firstday.project.reward.presentation;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 전용 리워드 API. 게이트웨이가 로그인 여부(인증)는 확인해주지만 role별 접근 제어(인가)는
 * 각 서비스 책임이라, 여기서 요청자의 role이 ADMIN인지 직접 검증한다
 * (board-service ProjectNotice.validateOwnership과 동일한 관례).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/rewards")
public class RewardAdminController {

    private final RewardService rewardService;

    /** 공개(진행중) 리워드의 수량 축소 — 부득이한 축소만, 이미 판매된 수량 밑으로는 불가. 크리에이터는 늘리기만 가능. */
    @PatchMapping("/{rewardId}/quantity")
    public ApiResponse<RewardResponse> decreaseQuantity(@RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole,
                                                         @PathVariable Long rewardId,
                                                         @Valid @RequestBody RewardQuantityDecreaseRequest request) {
        requireAdmin(requesterRole);
        return ApiResponse.ok(rewardService.decreaseQuantity(rewardId, request.amount()));
    }

    /** 공개(진행중) 리워드 비활성화 — 크리에이터는 이 권한이 없다, 관리자 전용. */
    @DeleteMapping("/{rewardId}")
    public ApiResponse<Void> deactivate(@RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole,
                                         @PathVariable Long rewardId) {
        requireAdmin(requesterRole);
        rewardService.deactivate(rewardId);
        return ApiResponse.ok(null);
    }

    private void requireAdmin(UserRole requesterRole) {
        if (requesterRole != UserRole.ADMIN) {
            throw new IllegalArgumentException("관리자만 접근할 수 있습니다.");
        }
    }
}
