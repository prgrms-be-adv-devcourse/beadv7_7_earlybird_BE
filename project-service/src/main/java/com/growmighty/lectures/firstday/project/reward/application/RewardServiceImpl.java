package com.growmighty.lectures.firstday.project.reward.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.request.RewardCreateRequest;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.request.RewardUpdateRequest;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RewardServiceImpl implements RewardService {
    private final RewardRepository rewardRepository;
    private final ProjectRepository projectRepository;

    @Override
    @Transactional
    // TODO(팀): 등록 가능한 상태(PENDING_REVIEW?)인지 검증 — 존재 여부는 아래에서 확인함
    public RewardResponse register(Long projectId, RewardCreateRequest request) {
        if (!projectRepository.existsById(projectId)) {
            throw new EntityNotFoundException("존재하지 않는 프로젝트입니다. projectId=" + projectId);
        }
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

    @Override
    @Transactional
    public RewardResponse update(Long rewardId, RewardUpdateRequest request) {
        Reward reward = getRewardEntity(rewardId);
        Optional<Project> project = findProject(reward.getProjectId());
        if (project.isPresent() && project.get().isClosed()) {
            throw new IllegalStateException(
                "종료된 프로젝트(성공/실패/취소)의 리워드는 수정할 수 없습니다. 현재 상태=" + project.get().getStatus());
        }
        if (project.isPresent() && project.get().isPublished()) {
            if (request.name() != null || request.description() != null
                    || request.price() != null || request.totalQuantity() != null) {
                throw new IllegalArgumentException("공개된 프로젝트의 리워드는 수량 추가(increaseQuantity)만 가능합니다.");
            }
            if (request.increaseQuantity() == null) {
                throw new IllegalArgumentException("추가할 수량(increaseQuantity)을 입력해주세요.");
            }
            reward.increaseQuantity(request.increaseQuantity());
        } else {
            if (request.increaseQuantity() != null) {
                throw new IllegalArgumentException("공개 전에는 increaseQuantity 대신 totalQuantity로 수량을 직접 지정해주세요.");
            }
            reward.updateBeforePublish(request.name(), request.description(), request.price(), request.totalQuantity());
        }
        return RewardResponse.from(reward);
    }

    @Override
    @Transactional
    public void delete(Long rewardId) {
        Reward reward = getRewardEntity(rewardId);
        Optional<Project> project = findProject(reward.getProjectId());
        if (project.isPresent() && project.get().isPublished()) {
            reward.deactivate();
        } else {
            rewardRepository.delete(reward);
        }
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

    /**
     * update()/delete()의 published 여부 판단용. 일반 findById는 트랜잭션의 REPEATABLE READ
     * 스냅샷에 갇혀 동시에 승인(approve)된 최신 상태를 못 볼 수 있어, 공유 락으로 최신 커밋 상태를 읽는다.
     * register()가 프로젝트 존재 여부를 검증하지 않고(TODO), Project.delete()도 참조 중인 리워드
     * 여부를 확인하지 않아 부모 프로젝트가 사라진 "고아" 리워드가 있을 수 있다 — 이 경우 404로
     * 막아버리면 그 리워드를 영영 정리할 방법이 없으므로, 존재하지 않으면 "공개 전"과 동일하게
     * 취급한다(자유 수정/하드 삭제 허용).
     */
    private Optional<Project> findProject(Long projectId) {
        return projectRepository.findByIdForStatusCheck(projectId);
    }
}
