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
 * RewardServiceImpl의 @Retryable 래퍼가 재시도마다 이 빈의 프록시를 다시 호출해야, 시도마다
 * 물리적으로 새 트랜잭션에서 실행된다는 게 (자기 자신 호출이 아니라) 코드 구조로도 명확해진다.
 * 트랜잭션 메서드는 반드시 public이어야 한다 — Spring 프록시 기반 @Transactional은 기본적으로
 * public 메서드만 인터셉트하고, package-private/protected는 어노테이션이 있어도 조용히 무시한다.
 */
@Component
@RequiredArgsConstructor
public class RewardStockTransactionExecutor {
    private final RewardRepository rewardRepository;
    private final ObjectProvider<ProjectService> projectServiceProvider;
    private final StockChangeLogRepository stockChangeLogRepository;

    @Transactional
    public void decreaseStock(Long rewardId, int quantity, Long orderId) {
        if (!tryRegisterStockChange(orderId, rewardId, StockChangeOperation.DECREASE)) {
            return; // 이미 처리된 요청 — 재고를 다시 반영하지 않고 조용히 종료(#195, 200 no-op)
        }
        Reward reward = getRewardEntity(rewardId);
        findProjectStatus(reward.getProjectId())
            .filter(ProjectStatusView::open)
            .orElseThrow(() -> new IllegalStateException(
                "마감되었거나 진행중이 아닌 프로젝트의 리워드는 주문할 수 없습니다. rewardId=" + rewardId));
        reward.decreaseStock(quantity);
    }

    @Transactional
    public void restoreStock(Long rewardId, int quantity, Long orderId) {
        if (!tryRegisterStockChange(orderId, rewardId, StockChangeOperation.RESTORE)) {
            return; // 이미 처리된 요청 — 재고를 다시 반영하지 않고 조용히 종료(#195, 200 no-op)
        }
        getRewardEntity(rewardId).restoreStock(quantity);
    }

    /**
     * (orderId, rewardId, operation) 조합을 stock_change_logs에 기록한다 — 유니크 제약 위반이면
     * 이미 처리된 요청이라는 뜻이라 false를 돌려줘 호출자가 재고 변경을 건너뛰게 한다.
     * TODO(#195 후속): 이 catch가 트랜잭션을 rollback-only로 오염시켜 커밋 시점에
     * UnexpectedRollbackException을 유발하는 별도 버그가 있음 — 아직 미해결.
     */
    private boolean tryRegisterStockChange(Long orderId, Long rewardId, StockChangeOperation operation) {
        try {
            stockChangeLogRepository.save(StockChangeLog.of(orderId, rewardId, operation));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    private Reward getRewardEntity(Long rewardId) {
        return rewardRepository.findById(rewardId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 리워드입니다. rewardId=" + rewardId));
    }

    private Optional<ProjectStatusView> findProjectStatus(Long projectId) {
        return projectServiceProvider.getObject().findStatusView(projectId);
    }
}
