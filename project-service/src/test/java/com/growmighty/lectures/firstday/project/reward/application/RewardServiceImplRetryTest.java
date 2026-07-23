package com.growmighty.lectures.firstday.project.reward.application;

import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import com.growmighty.lectures.firstday.project.project.application.ProjectStatusView;
import com.growmighty.lectures.firstday.project.exception.ConcurrentUpdateFailedException;
import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.request.RewardUpdateRequest;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.response.RewardResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * decreaseQuantity/update의 @Retryable+@Recover가 실제로 동작하는지 검증한다.
 * 순수 Mockito 단위 테스트(new RewardServiceImpl(...))는 Spring AOP 프록시를 거치지 않아
 * @Retryable 자체가 발동하지 않으므로, @EnableRetry가 켜진 최소 스프링 컨텍스트에서
 * 빈으로 등록된 프록시 인스턴스(rewardService)를 통해 호출해야 한다.
 */
@SpringJUnitConfig(RewardServiceImplRetryTest.RetryTestConfig.class)
class RewardServiceImplRetryTest {

    private static final ProjectStatusView PUBLISHED_OPEN_VIEW =
            new ProjectStatusView(true, false, true, "IN_PROGRESS");

    @Configuration
    @EnableRetry(order = Ordered.LOWEST_PRECEDENCE - 1)
    static class RetryTestConfig {
        @Bean
        RewardRepository rewardRepository() {
            return mock(RewardRepository.class);
        }

        @Bean
        ProjectService projectService() {
            return mock(ProjectService.class);
        }

        @SuppressWarnings("unchecked")
        @Bean
        ObjectProvider<ProjectService> projectServiceProvider(ProjectService projectService) {
            ObjectProvider<ProjectService> provider = mock(ObjectProvider.class);
            when(provider.getObject()).thenReturn(projectService);
            return provider;
        }

        @Bean
        RewardService rewardService(RewardRepository rewardRepository, ObjectProvider<ProjectService> projectServiceProvider) {
            return new RewardServiceImpl(rewardRepository, projectServiceProvider);
        }
    }

    @Autowired
    private RewardService rewardService;
    @Autowired
    private RewardRepository rewardRepository;
    @Autowired
    private ProjectService projectService;

    private Reward reward;

    @BeforeEach
    void setUp() {
        reset(rewardRepository, projectService);
        reward = Reward.register(1L, "노트커버", "설명", BigDecimal.valueOf(10_000), 10);
        when(rewardRepository.findById(anyLong())).thenReturn(Optional.of(reward));
    }

    @Test
    @DisplayName("decreaseQuantity: 락 충돌이 재시도 범위(3회) 안에서 풀리면 정상 반영된다")
    void decreaseQuantity_retriesUntilSuccess() {
        when(projectService.findStatusView(anyLong()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Reward.class, 1L))
                .thenThrow(new ObjectOptimisticLockingFailureException(Reward.class, 1L))
                .thenReturn(Optional.of(PUBLISHED_OPEN_VIEW));

        RewardResponse response = rewardService.decreaseQuantity(1L, 2);

        assertThat(response.totalQuantity()).isEqualTo(8);
        verify(projectService, times(3)).findStatusView(anyLong());
    }

    @Test
    @DisplayName("decreaseQuantity: 재시도를 다 소진하면 ConcurrentUpdateFailedException으로 변환된다")
    void decreaseQuantity_exhaustsRetries_throwsConcurrentUpdateFailed() {
        when(projectService.findStatusView(anyLong()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Reward.class, 1L));

        assertThatThrownBy(() -> rewardService.decreaseQuantity(1L, 2))
                .isInstanceOf(ConcurrentUpdateFailedException.class);
        verify(projectService, times(3)).findStatusView(anyLong());
    }

    @Test
    @DisplayName("decreaseQuantity: 락 충돌이 아닌 검증 예외는 재시도 없이 원래 타입 그대로 전파된다")
    void decreaseQuantity_nonLockException_notMasked() {
        when(projectService.findStatusView(anyLong())).thenReturn(Optional.of(PUBLISHED_OPEN_VIEW));

        assertThatThrownBy(() -> rewardService.decreaseQuantity(1L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1개 이상");
        verify(projectService, times(1)).findStatusView(anyLong());
    }

    @Test
    @DisplayName("update: 공개 후 increaseQuantity 경로에서 락 충돌이 재시도 범위 안에서 풀리면 정상 반영된다")
    void update_increaseQuantity_retriesUntilSuccess() {
        when(projectService.findStatusView(anyLong()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Reward.class, 1L))
                .thenReturn(Optional.of(PUBLISHED_OPEN_VIEW));
        RewardUpdateRequest request = new RewardUpdateRequest(null, null, null, null, 5);

        RewardResponse response = rewardService.update(1L, 1L, request);

        assertThat(response.totalQuantity()).isEqualTo(15);
        verify(projectService, times(2)).findStatusView(anyLong());
    }

    @Test
    @DisplayName("update: 재시도를 다 소진하면 ConcurrentUpdateFailedException으로 변환된다")
    void update_exhaustsRetries_throwsConcurrentUpdateFailed() {
        when(projectService.findStatusView(anyLong()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Reward.class, 1L));
        RewardUpdateRequest request = new RewardUpdateRequest(null, null, null, null, 5);

        assertThatThrownBy(() -> rewardService.update(1L, 1L, request))
                .isInstanceOf(ConcurrentUpdateFailedException.class);
        verify(projectService, times(3)).findStatusView(anyLong());
    }

    @Test
    @DisplayName("update: 락 충돌이 아닌 검증 예외(increaseQuantity 누락)는 재시도 없이 원래 타입 그대로 전파된다")
    void update_nonLockException_notMasked() {
        when(projectService.findStatusView(anyLong())).thenReturn(Optional.of(PUBLISHED_OPEN_VIEW));
        RewardUpdateRequest request = new RewardUpdateRequest(null, null, null, null, null);

        assertThatThrownBy(() -> rewardService.update(1L, 1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("추가할 수량");
        verify(projectService, times(1)).findStatusView(anyLong());
    }
}
