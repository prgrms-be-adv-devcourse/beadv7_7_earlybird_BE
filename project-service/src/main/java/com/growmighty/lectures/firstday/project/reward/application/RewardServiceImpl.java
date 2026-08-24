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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private final RewardStockTransactionExecutor rewardStockTransactionExecutor;

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
        Optional<Reward> existing = rewardRepository.findByProjectIdAndIdempotencyKey(projectId, request.idempotencyKey());
        if (existing.isPresent()) {
            return RewardResponse.from(existing.get());
        }
        // ponytail: Project.create()와 동일한 이유로 진짜 동시 요청 레이스는 지금 생략한다
        // (같은 트랜잭션 안에서 DataIntegrityViolationException을 잡으면 UnexpectedRollbackException만
        // 대신 난다 — ProjectServiceImpl.create() 주석 참고).
        Reward reward = rewardRepository.save(request.toEntity(projectId));
        projectServiceProvider.getObject().reindex(projectId);
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
     * @Retryable — 공개 후 경로(increaseQuantity)가 다른 후원자의 동시 주문(decreaseStock)이 원자적
     * UPDATE로 증가시키는 같은 리워드의 @Version을 두고 경합할 수 있다. decreaseStock 자체는 더 이상
     * 낙관적 락을 쓰지 않지만(원자적 조건부 UPDATE), 그 UPDATE도 version을 함께 증가시키므로 이
     * 엔티티 기반 경로는 여전히 그 변경을 감지하고 재시도해야 한다. update()는 self-invocation이
     * 아니라 컨트롤러가 프록시를 통해 직접 호출하는 public 메서드라 별도 분리 없이 바로 @Retryable을
     * 붙일 수 있다. 파라미터 타입(Long, RewardUpdateRequest)이 decreaseQuantity류와 달라 기존
     * @Recover를 공유할 수 없으므로 전용 쌍을 아래에 별도로 둔다 — catch-all을 빠뜨리면 이 메서드의
     * IllegalArgumentException/IllegalStateException 검증 예외까지 500으로 마스킹되므로 필수.
     */
    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public RewardResponse update(Long rewardId, Long requesterId, RewardUpdateRequest request) {
        Reward reward = getRewardEntity(rewardId);
        ProjectStatusView project = findProjectStatus(reward.getProjectId())
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 프로젝트입니다. projectId=" + reward.getProjectId()));
        validateOwnership(project, requesterId);
        if (project.closed()) {
            throw new IllegalStateException(
                "종료된 프로젝트(성공/실패/취소)의 리워드는 수정할 수 없습니다. 현재 상태=" + project.status());
        }
        if (project.published()) {
            if (request.name() != null || request.description() != null
                    || request.price() != null || request.totalQuantity() != null || request.clearTotalQuantity()) {
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
            reward.updateBeforePublish(request.name(), request.description(), request.price(),
                request.totalQuantity(), request.clearTotalQuantity());
            if (request.name() != null) {
                projectServiceProvider.getObject().reindex(reward.getProjectId());
            }
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
        ProjectStatusView project = findProjectStatus(reward.getProjectId())
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 프로젝트입니다. projectId=" + reward.getProjectId()));
        validateOwnership(project, requesterId);
        if (project.published()) {
            throw new IllegalStateException("공개된 프로젝트의 리워드 비활성화는 관리자 전용 API를 이용하세요.");
        }
        rewardRepository.delete(reward);
        projectServiceProvider.getObject().reindex(reward.getProjectId());
    }

    /**
     * update()와 같은 이유로 @Retryable — 관리자가 수량을 줄이는 동안 다른 후원자의 주문
     * (decreaseStock, 원자적 UPDATE로 version 증가)이 같은 리워드에 겹치면 이 엔티티 기반 경로가
     * 낙관적 락 충돌을 겪을 수 있다. 반환 타입이 RewardResponse라 update()의 @Recover와는 파라미터
     * 시그니처가 달라 공유할 수 없다 — 아래 전용 @Recover 두 개를 별도로 둔다.
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
     * decreaseStock/restoreStock은 RewardStockTransactionExecutor 내부에서 낙관적 락(엔티티 flush) 대신
     * RewardRepository의 원자적 조건부 UPDATE(decreaseStockAtomic/restoreStockAtomic)를 쓰므로, 더 이상
     * ObjectOptimisticLockingFailureException이 나지 않아 @Retryable/@Recover가 필요 없다. 그래도 이
     * 래퍼(NOT_SUPPORTED)와 트랜잭션 경계(rewardStockTransactionExecutor의 @Transactional)는 여전히
     * 분리해야 한다 — 중복 요청(DataIntegrityViolationException)을 트랜잭션이 이미 끝난 뒤인 여기서
     * 잡아야 하기 때문이다. 트랜잭션 안에서 잡으면 이미 rollback-only로 표시된 트랜잭션을 커밋 시도하다
     * UnexpectedRollbackException이 난다(RewardStockTransactionExecutor.registerStockChange 참고).
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void decreaseStock(Long rewardId, int quantity, Long orderId) {
        try {
            rewardStockTransactionExecutor.decreaseStock(rewardId, quantity, orderId);
        } catch (DataIntegrityViolationException e) {
            // 이미 처리된 요청 — 재고를 다시 반영하지 않고 조용히 종료(#195, 200 no-op)
        }
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void restoreStock(Long rewardId, int quantity, Long orderId) {
        try {
            rewardStockTransactionExecutor.restoreStock(rewardId, quantity, orderId);
        } catch (DataIntegrityViolationException e) {
            // 이미 처리된 요청 — 재고를 다시 반영하지 않고 조용히 종료(#195, 200 no-op)
        }
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
     * register()/update()/delete()의 published 여부 판단용. ProjectService.findStatusView()가
     * 내부적으로 공유 락(최신 커밋 상태)으로 조회해준다 — 일반 조회는 REPEATABLE READ 스냅샷에
     * 갇혀 동시에 승인(approve)된 최신 상태를 못 볼 수 있기 때문. register()와 마찬가지로
     * update()/delete()도 프로젝트가 없으면 orElseThrow로 404 처리한다 — 프로젝트 삭제
     * (ProjectServiceImpl.deleteInternal)가 같은 트랜잭션에서 리워드를 먼저 지우므로 부모가
     * 사라진 "고아" 리워드는 정상 경로로는 생기지 않는다.
     */
    private Optional<ProjectStatusView> findProjectStatus(Long projectId) {
        return projectServiceProvider.getObject().findStatusView(projectId);
    }
}

