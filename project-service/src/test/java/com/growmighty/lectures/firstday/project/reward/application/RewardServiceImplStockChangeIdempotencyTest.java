package com.growmighty.lectures.firstday.project.reward.application;

import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import com.growmighty.lectures.firstday.project.project.application.ProjectStatusView;
import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
import com.growmighty.lectures.firstday.project.reward.infrastructure.StockChangeLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * decreaseStock/restoreStock의 (orderId, rewardId, operation) 멱등성 체크(#195)를 주로 검증하고,
 * decreaseStockAtomic이 아예 호출되지 않는 무제한 리워드 분기(totalQuantity=null)의 active 체크 회귀도 함께 막는다.
 */
class RewardServiceImplStockChangeIdempotencyTest {

    private static final ProjectStatusView PUBLISHED_OPEN_VIEW =
            new ProjectStatusView(true, false, true, "IN_PROGRESS", 1L);

    private final RewardRepository rewardRepository = mock(RewardRepository.class);
    private final ProjectService projectService = mock(ProjectService.class);
    private final StockChangeLogRepository stockChangeLogRepository = mock(StockChangeLogRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ProjectService> projectServiceProvider = mock(ObjectProvider.class);
    private final RewardStockTransactionExecutor rewardStockTransactionExecutor =
            new RewardStockTransactionExecutor(rewardRepository, projectServiceProvider, stockChangeLogRepository);
    private final RewardServiceImpl rewardService =
            new RewardServiceImpl(rewardRepository, projectServiceProvider, rewardStockTransactionExecutor);

    private Reward reward;

    @BeforeEach
    void setUp() {
        reward = Reward.register(1L, "노트커버", "설명", BigDecimal.valueOf(10_000), 10);
        when(rewardRepository.findById(anyLong())).thenReturn(Optional.of(reward));
        when(projectServiceProvider.getObject()).thenReturn(projectService);
        when(projectService.findStatusView(anyLong())).thenReturn(Optional.of(PUBLISHED_OPEN_VIEW));
        // decreaseStockAtomic/restoreStockAtomic은 실제로는 DB에서 원자적 조건부 UPDATE를 수행하지만,
        // 여기서는 순수 Mockito 목이라 그 UPDATE 효과를 도메인 메서드 호출로 흉내내 재현한다.
        when(rewardRepository.decreaseStockAtomic(anyLong(), anyInt())).thenAnswer(invocation -> {
            reward.decreaseStock(invocation.getArgument(1));
            return 1;
        });
        when(rewardRepository.restoreStockAtomic(anyLong(), anyInt())).thenAnswer(invocation -> {
            reward.restoreStock(invocation.getArgument(1));
            return 1;
        });
    }

    @Test
    @DisplayName("decreaseStock: 최초 요청이면 재고가 정상 차감된다")
    void decreaseStock_firstRequest_appliesStockChange() {
        rewardService.decreaseStock(1L, 2, 100L);

        assertThat(reward.getRemainingQuantity()).isEqualTo(8);
    }

    @Test
    @DisplayName("decreaseStock: 같은 (orderId, rewardId, DECREASE) 재요청이면 재고 변경 없이 조용히 종료된다")
    void decreaseStock_duplicateRequest_noOp() {
        when(stockChangeLogRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        rewardService.decreaseStock(1L, 2, 100L);

        assertThat(reward.getRemainingQuantity()).isEqualTo(10);
        verify(rewardRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("restoreStock: 최초 요청이면 재고가 정상 복원된다")
    void restoreStock_firstRequest_appliesStockChange() {
        reward.decreaseStock(3);

        rewardService.restoreStock(1L, 1, 200L);

        assertThat(reward.getRemainingQuantity()).isEqualTo(8);
    }

    @Test
    @DisplayName("restoreStock: 같은 (orderId, rewardId, RESTORE) 재요청이면 재고 변경 없이 조용히 종료된다")
    void restoreStock_duplicateRequest_noOp() {
        reward.decreaseStock(3);
        when(stockChangeLogRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        rewardService.restoreStock(1L, 1, 200L);

        assertThat(reward.getRemainingQuantity()).isEqualTo(7);
        verify(rewardRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("decreaseStock: 비활성화된 무제한 리워드는 decreaseStockAtomic을 타지 않아도 주문이 거부된다")
    void decreaseStock_inactiveUnlimitedReward_throws() {
        Reward unlimitedReward = Reward.register(1L, "무제한 굿즈", "설명", BigDecimal.valueOf(5_000), null);
        unlimitedReward.deactivate();
        when(rewardRepository.findById(anyLong())).thenReturn(Optional.of(unlimitedReward));

        assertThatThrownBy(() -> rewardService.decreaseStock(1L, 1, 300L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("판매 종료된 리워드는 주문할 수 없습니다");

        verify(rewardRepository, never()).decreaseStockAtomic(anyLong(), anyInt());
    }
}
