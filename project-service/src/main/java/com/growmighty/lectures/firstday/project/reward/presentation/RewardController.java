package com.growmighty.lectures.firstday.project.reward.presentation;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.request.RewardCreateRequest;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.request.RewardQuantityDecreaseRequest;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class RewardController {

    private final RewardService rewardService;

    @PostMapping("/api/v1/projects/{projectId}/rewards")
    public ApiResponse<RewardResponse> register(@PathVariable Long projectId,
                                                @RequestHeader(JwtHeaders.USER_ID) Long requesterId,
                                                @Valid @RequestBody RewardCreateRequest request) {
        return ApiResponse.ok(rewardService.register(projectId, requesterId, request));
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
    public ApiResponse<RewardResponse> update(@PathVariable Long rewardId,
                                              @RequestHeader(JwtHeaders.USER_ID) Long requesterId,
                                              @Valid @RequestBody RewardUpdateRequest request) {
        return ApiResponse.ok(rewardService.update(rewardId, requesterId, request));
    }

    /** 공개 전: 하드 삭제(창작자 전용). 공개 후엔 거부되며, 그 경우 관리자가 /deactivate를 대신 사용한다. */
    @DeleteMapping("/api/v1/rewards/{rewardId}")
    public ApiResponse<Void> delete(@PathVariable Long rewardId, @RequestHeader(JwtHeaders.USER_ID) Long requesterId) {
        rewardService.delete(rewardId, requesterId);
        return ApiResponse.ok(null);
    }

    /** 공개(진행중) 리워드의 수량 축소 — 부득이한 축소만, 이미 판매된 수량 밑으로는 불가. 크리에이터는 늘리기만 가능. */
    @PatchMapping("/api/v1/rewards/{rewardId}/quantity")
    public ApiResponse<RewardResponse> decreaseQuantity(@RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole,
                                                         @PathVariable Long rewardId,
                                                         @Valid @RequestBody RewardQuantityDecreaseRequest request) {
        requireAdmin(requesterRole);
        return ApiResponse.ok(rewardService.decreaseQuantity(rewardId, request.amount()));
    }

    /** 공개(진행중) 리워드 비활성화 — 크리에이터는 이 권한이 없다, 관리자 전용. delete()와 달리 레코드는 보존한다. */
    @PostMapping("/api/v1/rewards/{rewardId}/deactivate")
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
