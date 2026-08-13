package com.growmighty.lectures.firstday.project.reward.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import com.growmighty.lectures.firstday.project.project.application.ProjectStatusView;
import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import com.growmighty.lectures.firstday.project.reward.domain.StockChangeLog;
import com.growmighty.lectures.firstday.project.reward.domain.StockChangeOperation;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
import com.growmighty.lectures.firstday.project.reward.infrastructure.StockChangeLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * decreaseStock/restoreStock의 실제 트랜잭션 본문만 담당하는 별도 빈.
 * RewardServiceImpl이 이 빈의 프록시를 통해 호출해야, registerStockChange의 유니크 제약 위반
 * (DataIntegrityViolationException)을 트랜잭션이 완전히 끝난 뒤 RewardServiceImpl 쪽에서 잡을 수 있다
 * (registerStockChange 문서 참고) — 같은 트랜잭션 안에서 잡으면 이미 rollback-only로 표시된 트랜잭션을
 * 커밋 시도하다 UnexpectedRollbackException이 난다. 트랜잭션 메서드는 반드시 public이어야 한다 —
 * Spring 프록시 기반 @Transactional은 기본적으로 public 메서드만 인터셉트하고, package-private/
 * protected는 어노테이션이 있어도 조용히 무시한다.
 */
@Component
@RequiredArgsConstructor
public class RewardStockTransactionExecutor {
    private final RewardRepository rewardRepository;
    private final ObjectProvider<ProjectService> projectServiceProvider;
    private final StockChangeLogRepository stockChangeLogRepository;

    /**
     * 재고 차감은 엔티티를 읽어 수정 후 flush(낙관적 락)하는 대신 RewardRepository.decreaseStockAtomic로
     * 원자적 조건부 UPDATE를 실행한다 — 고경합 상황에서 재시도를 반복 소진해 재고가 남았는데도 실패하는
     * 문제를 없애고, DB WHERE 절 자체가 초과 판매를 막는다. quantity>0 검증과 무제한 리워드(totalQuantity
     * null) no-op 처리만 애플리케이션에서 미리 걸러내고, "활성 상태인가"/"재고가 충분한가"는 UPDATE의
     * WHERE 절이 원자적으로 검증한다 — 실패(영향 행 0건) 시에만 원인 구분용으로 다시 조회한다.
     */
    @Transactional
    public void decreaseStock(Long rewardId, int quantity, Long orderId) {
        registerStockChange(orderId, rewardId, StockChangeOperation.DECREASE);
        if (quantity <= 0) {
            throw new IllegalArgumentException("차감 수량은 1개 이상이어야 합니다.");
        }
        Reward reward = getRewardEntity(rewardId);
        findProjectStatus(reward.getProjectId())
            .filter(ProjectStatusView::open)
            .orElseThrow(() -> new IllegalStateException(
                "마감되었거나 진행중이 아닌 프로젝트의 리워드는 주문할 수 없습니다. rewardId=" + rewardId));
        // 무제한 리워드(totalQuantity=null)는 decreaseStockAtomic을 아예 호출하지 않아 그 WHERE절의
        // active=true 조건에 닿지 못한다 — active 체크를 여기서 먼저 해야 비활성화된 무제한 리워드도 막힌다.
        if (!reward.isActive()) {
            throw new IllegalStateException("판매 종료된 리워드는 주문할 수 없습니다. reward=" + reward.getName());
        }
        if (reward.getTotalQuantity() == null) {
            return;
        }
        int updated = rewardRepository.decreaseStockAtomic(rewardId, quantity);
        if (updated == 0) {
            Reward current = getRewardEntity(rewardId);
            if (!current.isActive()) {
                throw new IllegalStateException("판매 종료된 리워드는 주문할 수 없습니다. reward=" + current.getName());
            }
            throw new IllegalStateException(
                "재고가 부족합니다. reward=" + current.getName() + ", 재고=" + current.getRemainingQuantity()
                    + ", 요청=" + quantity);
        }
    }

    /** decreaseStock과 같은 이유로 원자적 조건부 UPDATE를 쓴다 — restoreStockAtomic 참고. */
    @Transactional
    public void restoreStock(Long rewardId, int quantity, Long orderId) {
        registerStockChange(orderId, rewardId, StockChangeOperation.RESTORE);
        if (quantity <= 0) {
            throw new IllegalArgumentException("복원 수량은 1개 이상이어야 합니다.");
        }
        Reward reward = getRewardEntity(rewardId);
        if (reward.getTotalQuantity() == null) {
            return;
        }
        int updated = rewardRepository.restoreStockAtomic(rewardId, quantity);
        if (updated == 0) {
            Reward current = getRewardEntity(rewardId);
            throw new IllegalStateException(
                "복원 후 재고가 총 수량을 초과할 수 없습니다. reward=" + current.getName()
                    + ", 재고=" + current.getRemainingQuantity() + ", 복원=" + quantity
                    + ", 총수량=" + current.getTotalQuantity());
        }
    }

    /**
     * (orderId, rewardId, operation) 조합을 stock_change_logs에 기록한다. 유니크 제약 위반
     * (DataIntegrityViolationException)을 여기서 catch하지 않고 그대로 던진다 — 이 시점이면 이미
     * Hibernate가 flush 실패로 현재 트랜잭션을 rollback-only로 표시한 뒤라, 여기서 catch하고
     * 메서드가 정상 반환되면 커밋 시도 자체가 UnexpectedRollbackException으로 실패한다. 예외를
     * 트랜잭션 경계(이 메서드) 밖으로 내보내야 Spring이 정상적인 rollback으로 깔끔하게 마무리하고,
     * "이미 처리된 요청"이라는 판단은 트랜잭션이 없는 RewardServiceImpl 쪽 try-catch에서 내린다.
     */
    private void registerStockChange(Long orderId, Long rewardId, StockChangeOperation operation) {
        stockChangeLogRepository.save(StockChangeLog.of(orderId, rewardId, operation));
    }

    private Reward getRewardEntity(Long rewardId) {
        return rewardRepository.findById(rewardId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 리워드입니다. rewardId=" + rewardId));
    }

    private Optional<ProjectStatusView> findProjectStatus(Long projectId) {
        return projectServiceProvider.getObject().findStatusView(projectId);
    }
}
