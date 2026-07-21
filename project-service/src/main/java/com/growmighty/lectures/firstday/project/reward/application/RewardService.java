package com.growmighty.lectures.firstday.project.reward.application;

import com.growmighty.lectures.firstday.project.reward.presentation.dto.request.RewardCreateRequest;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.request.RewardUpdateRequest;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.response.RewardResponse;

import java.util.List;

public interface RewardService {

    RewardResponse register(Long projectId, RewardCreateRequest request);

    List<RewardResponse> getRewardsByProject(Long projectId);

    RewardResponse getReward(Long rewardId);

    RewardResponse update(Long rewardId, RewardUpdateRequest request);

    void delete(Long rewardId);

    // ── 관리자 전용 (공개 중인 리워드의 수량 축소/비활성화는 크리에이터 권한 밖) ──────
    RewardResponse decreaseQuantity(Long rewardId, int amount);

    void deactivate(Long rewardId);

    // ── order-service가 호출하는 내부 API ──────────────────────────
    void decreaseStock(Long rewardId, int quantity);

    void restoreStock(Long rewardId, int quantity);

    // ── project 도메인이 호출하는 API (project-service 내부, 도메인 간) ──────
    /** 프로젝트가 마감(성공/실패/조기종료)될 때 그 프로젝트의 리워드를 전부 비활성화한다. */
    void deactivateAllByProject(Long projectId);

    /** 프로젝트가 삭제(공개 전 하드 삭제)될 때 그 프로젝트의 리워드를 전부 지운다. */
    void deleteAllByProject(Long projectId);
}
