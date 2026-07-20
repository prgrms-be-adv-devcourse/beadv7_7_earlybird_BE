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
    public RewardResponse register(Long projectId, RewardCreateRequest request) {
        Project project = findProject(projectId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 프로젝트입니다. projectId=" + projectId));
        if (project.isClosed()) {
            throw new IllegalStateException(
                "종료된 프로젝트(성공/실패/취소)에는 리워드를 추가할 수 없습니다. 현재 상태=" + project.getStatus());
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

    /**
     * decreaseStock/restoreStock과 같은 이유로 @Retryable — 공개 후 경로(increaseQuantity)가
     * 다른 후원자의 동시 주문(decreaseStock)과 같은 리워드의 @Version을 두고 경합할 수 있다.
     * update()는 self-invocation이 아니라 컨트롤러가 프록시를 통해 직접 호출하는 public
     * 메서드라 별도 분리 없이 바로 @Retryable을 붙일 수 있다. 파라미터 타입(Long,
     * RewardUpdateRequest)이 decreaseStock류와 달라 기존 @Recover를 공유할 수 없으므로
     * 전용 쌍을 아래에 별도로 둔다 — catch-all을 빠뜨리면 이 메서드의 IllegalArgumentException/
     * IllegalStateException 검증 예외까지 500으로 마스킹되므로 필수.
     */
    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
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

    @Recover
    public RewardResponse recoverUpdateConflict(ObjectOptimisticLockingFailureException e, Long rewardId, RewardUpdateRequest request) {
        throw new ConcurrentUpdateFailedException(
            "리워드 수정 중 동시 수정 충돌이 반복되어 실패했습니다. rewardId=" + rewardId);
    }

    @Recover
    public RewardResponse recoverUpdateOther(RuntimeException e, Long rewardId, RewardUpdateRequest request) {
        throw e;
    }

    @Override
    @Transactional
    public void delete(Long rewardId) {
        Reward reward = getRewardEntity(rewardId);
        Optional<Project> project = findProject(reward.getProjectId());
        if (project.isPresent() && project.get().isPublished()) {
            throw new IllegalStateException("공개된 프로젝트의 리워드 비활성화는 관리자 전용 API를 이용하세요.");
        }
        rewardRepository.delete(reward);
    }

    /**
     * decreaseStock/restoreStock과 같은 이유로 @Retryable — 관리자가 수량을 줄이는 동안 다른
     * 후원자의 주문(decreaseStock)이 같은 리워드에 겹치면 낙관적 락 충돌이 날 수 있다.
     * 반환 타입이 RewardResponse라 decreaseStock/restoreStock의 void @Recover와는 시그니처가
     * 갈려서(반환 타입 불일치) 공유할 수 없다 — 아래 전용 @Recover 두 개를 별도로 둔다.
     */
    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public RewardResponse decreaseQuantity(Long rewardId, int amount) {
        Reward reward = getRewardEntity(rewardId);
        requirePublishedAndOpen(reward.getProjectId());
        reward.decreaseQuantity(amount);
        return RewardResponse.from(reward);
    }

    @Recover
    public RewardResponse recoverDecreaseQuantityConflict(ObjectOptimisticLockingFailureException e, Long rewardId, int amount) {
        throw new ConcurrentUpdateFailedException(
            "리워드 수량 축소 중 동시 수정 충돌이 반복되어 실패했습니다. rewardId=" + rewardId + ", amount=" + amount);
    }

    @Recover
    public RewardResponse recoverDecreaseQuantityOther(RuntimeException e, Long rewardId, int amount) {
        throw e;
    }

    @Override
    @Transactional
    public void deactivate(Long rewardId) {
        Reward reward = getRewardEntity(rewardId);
        requirePublishedAndOpen(reward.getProjectId());
        reward.deactivate();
    }

    /** 관리자 전용 API 대상 검증 — 지금 공개 중(진행중)인 리워드만 해당, 공개 전/종료된 프로젝트는 대상 아님. */
    private void requirePublishedAndOpen(Long projectId) {
        Project project = findProject(projectId)
            .orElseThrow(() -> new IllegalStateException("공개 중(진행중)인 프로젝트의 리워드만 대상입니다. projectId=" + projectId));
        if (!project.isPublished() || project.isClosed()) {
            throw new IllegalStateException("공개 중(진행중)인 프로젝트의 리워드만 대상입니다. 현재 상태=" + project.getStatus());
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

    /**
     * decreaseStock/restoreStock 둘 다 파라미터 시그니처(Long, int)가 같아서, Spring Retry는 이
     * @Recover를 "어느 쪽에서 재시도가 소진됐는지" 구분하지 않고 예외 타입+파라미터 시그니처로만
     * 매칭한다. 실제로 시그니처가 같은 @Recover 두 개를 두고 실험해본 결과, decreaseStock이든
     * restoreStock이든 항상 먼저 선언된 쪽만 호출되고 나머지 하나는 절대 안 불림(랜덤도, 원본
     * 메서드별 구분도 아님) — 그래서 둘로 나누는 건 죽은 코드만 남기고, 두 작업을 모두 포괄하는
     * 문구 하나로 통일하는 게 맞다.
     */
    @Recover
    public void recoverStockChangeConflict(ObjectOptimisticLockingFailureException e, Long rewardId, int quantity) {
        throw new ConcurrentUpdateFailedException(
            "재고 변경(차감/복원) 중 동시 수정 충돌이 반복되어 실패했습니다. rewardId=" + rewardId + ", quantity=" + quantity);
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

    /**
     * decreaseStock과 같은 이유로 @Retryable — 동시 취소·환불로 여러 restoreStock 요청이
     * 같은 리워드에 몰리면 낙관적 락 충돌이 날 수 있어, 재시도 없이는 정상적인 동시 복원 요청도
     * 그냥 실패해버린다.
     */
    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
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
