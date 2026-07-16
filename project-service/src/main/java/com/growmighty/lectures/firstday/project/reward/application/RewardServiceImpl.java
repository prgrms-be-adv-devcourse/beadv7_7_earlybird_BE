package com.growmighty.lectures.firstday.project.reward.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.request.RewardCreateRequest;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.response.RewardResponse;
import com.growmighty.lectures.firstday.project.reward.application.exception.ConcurrentUpdateFailedException;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RewardServiceImpl implements RewardService {
    private final RewardRepository rewardRepository;

    @Override
    @Transactional
    public RewardResponse register(Long projectId, RewardCreateRequest request) {
        // TODO(팀): 존재하는 프로젝트인지 + 등록 가능한 상태(PENDING_REVIEW?)인지 검증
        Reward reward = rewardRepository.save(request.toEntity(projectId));
        return RewardResponse.from(reward);
    }

    @Override
    public List<RewardResponse> getRewardsByProject(Long projectId) {
        return rewardRepository.findByProjectId(projectId).stream()
            .map(RewardResponse::from)
            .toList();
    }

    @Override
    public RewardResponse getReward(Long rewardId) {
        return RewardResponse.from(getRewardEntity(rewardId));
    }

    /**
     * @Retryable이 @Transactional을 감싸도록 순서가 보장돼야 한다(ProjectServiceApplication의 @EnableRetry order 설정).
     * 그래야 낙관적 락 충돌로 커밋이 실패했을 때 매 재시도가 새 트랜잭션에서 엔티티를 다시 읽어온다.
     */
    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public void decreaseStock(Long rewardId, int quantity) {
        getRewardEntity(rewardId).decreaseStock(quantity);
    }

    @Recover
    public void recoverDecreaseStock(ObjectOptimisticLockingFailureException e, Long rewardId, int quantity) {
        throw new ConcurrentUpdateFailedException(
            "재고 차감 중 동시 수정 충돌이 반복되어 실패했습니다. rewardId=" + rewardId + ", quantity=" + quantity);
    }

    /**
     * @Recover가 하나라도 등록되면 Spring Retry는 retryFor에 없는 예외(예: 재고 부족)까지도
     * "재시도 소진"으로 취급해 복구 메서드를 찾는다. 시그니처가 안 맞으면 원본 예외를 삼키고
     * ExhaustedRetryException(500)을 던지므로, 그런 예외는 여기서 그대로 다시 던져 원래 처리 경로(409/400)로 보낸다.
     */
    @Recover
    public void recoverOther(RuntimeException e, Long rewardId, int quantity) {
        throw e;
    }

    @Override
    @Transactional
    public void restoreStock(Long rewardId, int quantity) {
        getRewardEntity(rewardId).restoreStock(quantity);
    }

    private Reward getRewardEntity(Long rewardId) {
        return rewardRepository.findById(rewardId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 리워드입니다. rewardId=" + rewardId));
    }
}
