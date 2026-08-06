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
        registerStockChange(orderId, rewardId, StockChangeOperation.DECREASE);
        Reward reward = getRewardEntity(rewardId);
        findProjectStatus(reward.getProjectId())
            .filter(ProjectStatusView::open)
            .orElseThrow(() -> new IllegalStateException(
                "마감되었거나 진행중이 아닌 프로젝트의 리워드는 주문할 수 없습니다. rewardId=" + rewardId));
        reward.decreaseStock(quantity);
    }

    @Transactional
    public void restoreStock(Long rewardId, int quantity, Long orderId) {
        registerStockChange(orderId, rewardId, StockChangeOperation.RESTORE);
        getRewardEntity(rewardId).restoreStock(quantity);
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
