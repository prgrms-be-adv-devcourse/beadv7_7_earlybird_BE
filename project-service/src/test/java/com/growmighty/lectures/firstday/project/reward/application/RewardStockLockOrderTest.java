package com.growmighty.lectures.firstday.project.reward.application;

import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import com.growmighty.lectures.firstday.project.project.application.ProjectStatusView;
import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import com.growmighty.lectures.firstday.project.reward.domain.StockChangeLog;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
import com.growmighty.lectures.firstday.project.reward.infrastructure.StockChangeLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * 재고 변경 트랜잭션 안에서 <b>락을 잡는 순서</b>를 고정한다. 이 순서가 어긋나면 데드락이 난다.
 *
 * <p>stock_change_logs.reward_id에는 rewards를 가리키는 FK가 있어서, 멱등 로그 INSERT는 참조 정합성
 * 검증을 위해 부모 행(rewards)에 공유(S) 락을 건다. 이 INSERT가 재고 UPDATE보다 먼저 실행되면 한
 * 트랜잭션이 "S 획득 → 같은 행 X 요청"이 되어, 동시 요청들이 전부 S를 쥔 채 서로의 X를 기다리는
 * S→X 업그레이드 데드락에 걸린다(100 VU 실측에서 요청의 80%가 이 데드락으로 실패했다).
 *
 * <p>순서를 코드로만 지켜두면 나중에 "가독성 정리" 같은 이유로 조용히 되돌아가므로 테스트로 못박는다.
 */
@ExtendWith(MockitoExtension.class)
class RewardStockLockOrderTest {

    @Mock
    private RewardRepository rewardRepository;
    @Mock
    private StockChangeLogRepository stockChangeLogRepository;
    @Mock
    private ObjectProvider<ProjectService> projectServiceProvider;
    @Mock
    private ProjectService projectService;

    private RewardStockTransactionExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new RewardStockTransactionExecutor(rewardRepository, projectServiceProvider, stockChangeLogRepository);
    }

    @Test
    @DisplayName("재고 차감은 UPDATE를 먼저 하고 멱등 로그를 나중에 남긴다 (S→X 업그레이드 데드락 방지)")
    void decreaseStock_updatesStockBeforeWritingIdempotencyLog() {
        Reward reward = limitedReward();
        when(rewardRepository.findById(1L)).thenReturn(Optional.of(reward));
        when(projectServiceProvider.getObject()).thenReturn(projectService);
        when(projectService.findStatusView(anyLong()))
                .thenReturn(Optional.of(new ProjectStatusView(true, false, true, "IN_PROGRESS", 1L)));
        when(rewardRepository.decreaseStockAtomic(1L, 1)).thenReturn(1);

        executor.decreaseStock(1L, 1, 7L);

        InOrder order = inOrder(rewardRepository, stockChangeLogRepository);
        order.verify(rewardRepository).decreaseStockAtomic(1L, 1);
        order.verify(stockChangeLogRepository).save(any(StockChangeLog.class));
    }

    @Test
    @DisplayName("재고 복원도 UPDATE를 먼저 하고 멱등 로그를 나중에 남긴다")
    void restoreStock_updatesStockBeforeWritingIdempotencyLog() {
        Reward reward = limitedReward();
        when(rewardRepository.findById(1L)).thenReturn(Optional.of(reward));
        when(rewardRepository.restoreStockAtomic(1L, 1)).thenReturn(1);

        executor.restoreStock(1L, 1, 7L);

        InOrder order = inOrder(rewardRepository, stockChangeLogRepository);
        order.verify(rewardRepository).restoreStockAtomic(1L, 1);
        order.verify(stockChangeLogRepository).save(any(StockChangeLog.class));
    }

    @Test
    @DisplayName("재고 차감이 실패하면(영향 행 0건) 멱등 로그를 남기지 않는다")
    void decreaseStock_whenNoRowUpdated_doesNotWriteIdempotencyLog() {
        Reward reward = limitedReward();
        when(rewardRepository.findById(1L)).thenReturn(Optional.of(reward));
        when(projectServiceProvider.getObject()).thenReturn(projectService);
        when(projectService.findStatusView(anyLong()))
                .thenReturn(Optional.of(new ProjectStatusView(true, false, true, "IN_PROGRESS", 1L)));
        when(rewardRepository.decreaseStockAtomic(anyLong(), anyInt())).thenReturn(0);

        try {
            executor.decreaseStock(1L, 1, 7L);
        } catch (IllegalStateException expected) {
            // 재고 부족 — 이 경로에서는 로그를 남기지 않아야 한다
        }

        org.mockito.Mockito.verify(stockChangeLogRepository, org.mockito.Mockito.never())
                .save(any(StockChangeLog.class));
    }

    private Reward limitedReward() {
        return Reward.register(1L, UUID.randomUUID(), "한정 리워드", "설명", BigDecimal.valueOf(10_000), 300);
    }
}
