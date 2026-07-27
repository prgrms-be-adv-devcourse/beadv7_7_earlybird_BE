package com.growmighty.lectures.firstday.project.reward.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import com.growmighty.lectures.firstday.project.project.application.ProjectStatusView;
import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.request.RewardCreateRequest;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.request.RewardUpdateRequest;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.response.RewardResponse;
import com.growmighty.lectures.firstday.project.exception.ConcurrentUpdateFailedException;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
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
    // ProjectServiceImpl이 반대로 RewardService(ObjectProvider<RewardService>)를 필요로 해서
    // 생기는 순환 빈 의존을 끊기 위해 ObjectProvider로 지연 조회한다.
    private final ObjectProvider<ProjectService> projectServiceProvider;

    @Override
    @Transactional
    public RewardResponse register(Long projectId, Long requesterId, RewardCreateRequest request) {
        ProjectStatusView project = findProjectStatus(projectId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 프로젝트입니다. projectId=" + projectId));
        validateOwnership(project, requesterId);
        if (project.closed()) {
            throw new IllegalStateException(
                "종료된 프로젝트(성공/실패/취소)에는 리워드를 추가할 수 없습니다. 현재 상태=" + project.status());
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
    public RewardResponse update(Long rewardId, Long requesterId, RewardUpdateRequest request) {
        Reward reward = getRewardEntity(rewardId);
        Optional<ProjectStatusView> project = findProjectStatus(reward.getProjectId());
        project.ifPresent(p -> validateOwnership(p, requesterId));
        if (project.isPresent() && project.get().closed()) {
            throw new IllegalStateException(
                "종료된 프로젝트(성공/실패/취소)의 리워드는 수정할 수 없습니다. 현재 상태=" + project.get().status());
        }
        if (project.isPresent() && project.get().published()) {
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
    public RewardResponse recoverUpdateConflict(ObjectOptimisticLockingFailureException e, Long rewardId, Long requesterId, RewardUpdateRequest request) {
        throw new ConcurrentUpdateFailedException(
            "리워드 수정 중 동시 수정 충돌이 반복되어 실패했습니다. rewardId=" + rewardId);
    }

    @Recover
    public RewardResponse recoverUpdateOther(RuntimeException e, Long rewardId, Long requesterId, RewardUpdateRequest request) {
        throw e;
    }

    @Override
    @Transactional
    public void delete(Long rewardId, Long requesterId) {
        Reward reward = getRewardEntity(rewardId);
        Optional<ProjectStatusView> project = findProjectStatus(reward.getProjectId());
        project.ifPresent(p -> validateOwnership(p, requesterId));
        if (project.isPresent() && project.get().published()) {
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
        ProjectStatusView project = findProjectStatus(projectId)
            .orElseThrow(() -> new IllegalStateException("공개 중(진행중)인 프로젝트의 리워드만 대상입니다. projectId=" + projectId));
        if (!project.published() || project.closed()) {
            throw new IllegalStateException("공개 중(진행중)인 프로젝트의 리워드만 대상입니다. 현재 상태=" + project.status());
        }
    }

    /**
     * @Retryable이 @Transactional을 감싸도록 순서가 보장돼야 한다(ProjectServiceApplication의 @EnableRetry order 설정).
     * 그래야 낙관적 락 충돌로 커밋이 실패했을 때 매 재시도가 새 트랜잭션에서 엔티티를 다시 읽어온다.
     * 프로젝트가 지금 이 순간 열려있는지(ProjectStatusView.open())도 같이 확인한다 — 마감 배치가 아직
     * 안 돈 상태에서도 마감시각이 지났으면 즉시 주문을 막기 위함(마감 배치 주기와 무관하게 동작).
     */
    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50), recover = "recoverDecreaseStockConflict")
    @Transactional
    public void decreaseStock(Long rewardId, int quantity) {
        Reward reward = getRewardEntity(rewardId);
        findProjectStatus(reward.getProjectId())
            .filter(ProjectStatusView::open)
            .orElseThrow(() -> new IllegalStateException(
                "마감되었거나 진행중이 아닌 프로젝트의 리워드는 주문할 수 없습니다. rewardId=" + rewardId));
        reward.decreaseStock(quantity);
    }

    /**
     * decreaseStock/restoreStock이 파라미터 시그니처(Long, int)가 같아서, operation별로 서로 다른
     * @Recover를 시그니처 자동 매칭에 맡기면 둘 다 먼저 선언된 쪽만 호출되는 문제가 있었다.
     * @Retryable의 recover 속성으로 이름을 직접 지정해 그 모호성을 없앴는데, recover 속성은
     * 메서드 하나만 가리킬 수 있어서 "락 충돌 전용 + catch-all" 2단 구조를 그대로 못 쓴다 — 대신
     * 이 메서드 하나가 RuntimeException을 폭넓게 받고 instanceof로 분기한다. else(원본 재던지기)가
     * 없으면 retryFor에 없는 예외(재고 부족 등)까지 여기로 흘러들어와 매칭 실패로 500이 되므로 필수.
     */
    @Recover
    public void recoverDecreaseStockConflict(RuntimeException e, Long rewardId, int quantity) {
        if (e instanceof ObjectOptimisticLockingFailureException) {
            throw new ConcurrentUpdateFailedException(
                "재고 차감 중 동시 수정 충돌이 반복되어 실패했습니다. rewardId=" + rewardId + ", quantity=" + quantity);
        }
        throw e;
    }

    /**
     * decreaseStock과 같은 이유로 @Retryable — 동시 취소·환불로 여러 restoreStock 요청이
     * 같은 리워드에 몰리면 낙관적 락 충돌이 날 수 있어, 재시도 없이는 정상적인 동시 복원 요청도
     * 그냥 실패해버린다.
     */
    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50), recover = "recoverRestoreStockConflict")
    @Transactional
    public void restoreStock(Long rewardId, int quantity) {
        getRewardEntity(rewardId).restoreStock(quantity);
    }

    /** recoverDecreaseStockConflict와 동일한 instanceof 분기 패턴 — restoreStock 전용 메시지. */
    @Recover
    public void recoverRestoreStockConflict(RuntimeException e, Long rewardId, int quantity) {
        if (e instanceof ObjectOptimisticLockingFailureException) {
            throw new ConcurrentUpdateFailedException(
                "재고 복원 중 동시 수정 충돌이 반복되어 실패했습니다. rewardId=" + rewardId + ", quantity=" + quantity);
        }
        throw e;
    }

    /** ProjectServiceImpl.validateOwnership과 동일한 관례 — 리워드는 자기 creatorId가 없어 부모 프로젝트 걸 본다. */
    private void validateOwnership(ProjectStatusView project, Long requesterId) {
        if (!project.creatorId().equals(requesterId)) {
            throw new IllegalArgumentException("본인이 등록한 프로젝트의 리워드만 관리할 수 있습니다.");
        }
    }

    @Override
    @Transactional
    public void deactivateAllByProject(Long projectId) {
        rewardRepository.findByProjectId(projectId).forEach(Reward::deactivate);
    }

    @Override
    @Transactional
    public void deleteAllByProject(Long projectId) {
        rewardRepository.deleteByProjectId(projectId);
    }

    private Reward getRewardEntity(Long rewardId) {
        return rewardRepository.findById(rewardId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 리워드입니다. rewardId=" + rewardId));
    }

    /**
     * update()/delete()의 published 여부 판단용. ProjectService.findStatusView()가 내부적으로
     * 공유 락(최신 커밋 상태)으로 조회해준다 — 일반 조회는 REPEATABLE READ 스냅샷에 갇혀 동시에
     * 승인(approve)된 최신 상태를 못 볼 수 있기 때문. register()가 프로젝트 존재 여부를 검증하지
     * 않고(TODO), Project.delete()도 참조 중인 리워드 여부를 확인하지 않아 부모 프로젝트가 사라진
     * "고아" 리워드가 있을 수 있다 — 이 경우 404로 막아버리면 그 리워드를 영영 정리할 방법이
     * 없으므로, 존재하지 않으면 "공개 전"과 동일하게 취급한다(자유 수정/하드 삭제 허용).
     */
    private Optional<ProjectStatusView> findProjectStatus(Long projectId) {
        return projectServiceProvider.getObject().findStatusView(projectId);
    }
}
