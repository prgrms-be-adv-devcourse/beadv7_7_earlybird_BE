package com.growmighty.lectures.firstday.project.project.application;

import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.application.port.FilePort;
import com.growmighty.lectures.firstday.project.project.application.port.OrderPort;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.domain.ProjectStatus;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectDeadlineExtendRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectResponse;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.project.exception.ConcurrentUpdateFailedException;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * extendDeadline/closeProjectByDeadline의 @Retryable+@Recover가 실제로 동작하는지 검증한다.
 * RewardServiceImplRetryTest와 같은 이유로 순수 Mockito 단위 테스트는 Spring AOP 프록시를 거치지
 * 않아 @Retryable이 발동하지 않으므로, @EnableRetry가 켜진 최소 스프링 컨텍스트의 빈(projectService)을
 * 통해 호출해야 한다.
 */
@SpringJUnitConfig(ProjectServiceImplRetryTest.RetryTestConfig.class)
class ProjectServiceImplRetryTest {

    @Configuration
    @EnableRetry(order = Ordered.LOWEST_PRECEDENCE - 1)
    static class RetryTestConfig {
        @Bean
        ProjectRepository projectRepository() {
            return mock(ProjectRepository.class);
        }

        @Bean
        ProjectCategoryRepository projectCategoryRepository() {
            return mock(ProjectCategoryRepository.class);
        }

        @Bean
        RewardService rewardService() {
            return mock(RewardService.class);
        }

        // selfProvider는 여기서 mock으로 만들지 않는다 — closeEarly/closeProjectByDeadline이 이제
        // self-invocation(selfProvider.getObject().xxxInternal(...))으로 @Retryable을 태우므로,
        // 진짜 projectService 프록시를 가리켜야 재시도가 실제로 발동한다. ObjectProvider<T>는 Spring이
        // 순환 의존 걱정 없이 자동으로 지연 주입해주는 타입이라, 아래 projectService(...) 빈 메서드의
        // 파라미터로 그냥 선언만 하면 Spring이 실제 프록시를 돌려주는 진짜 ObjectProvider를 준다.

        @SuppressWarnings("unchecked")
        @Bean
        ObjectProvider<RewardService> rewardServiceProvider(RewardService rewardService) {
            ObjectProvider<RewardService> provider = mock(ObjectProvider.class);
            when(provider.getObject()).thenReturn(rewardService);
            return provider;
        }

        @Bean
        OrderPort orderPort() {
            return mock(OrderPort.class);
        }

        @Bean
        ProjectSearchPort projectSearchPort() {
            return mock(ProjectSearchPort.class);
        }

        @Bean
        FilePort filePort() {
            return mock(FilePort.class);
        }

        @Bean
        ProjectService projectService(ProjectRepository projectRepository, ProjectCategoryRepository projectCategoryRepository,
                                       ObjectProvider<ProjectService> selfProvider, ObjectProvider<RewardService> rewardServiceProvider,
                                       OrderPort orderPort, ProjectSearchPort searchPort, ApplicationEventPublisher eventPublisher, FilePort filePort) {
            return new ProjectServiceImpl(projectRepository, projectCategoryRepository, selfProvider, rewardServiceProvider, orderPort, searchPort, eventPublisher, filePort);
        }
    }

    @Autowired
    private ProjectService projectService;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private RewardService rewardService;
    @Autowired
    private OrderPort orderPort;

    private Project project;

    @BeforeEach
    void setUp() {
        reset(projectRepository, rewardService, orderPort);
        project = Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        project.approve();
    }

    @Test
    @DisplayName("extendDeadline: 락 충돌이 재시도 범위(3회) 안에서 풀리면 정상 반영된다")
    void extendDeadline_retriesUntilSuccess() {
        when(projectRepository.findById(anyLong()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Project.class, 1L))
                .thenThrow(new ObjectOptimisticLockingFailureException(Project.class, 1L))
                .thenReturn(Optional.of(project));

        LocalDate newEndAt = project.getEndAt().plusDays(10);
        ProjectResponse response = projectService.extendDeadline(1L, new ProjectDeadlineExtendRequest(newEndAt));

        assertThat(response.endAt()).isEqualTo(newEndAt);
        verify(projectRepository, times(3)).findById(anyLong());
    }

    @Test
    @DisplayName("extendDeadline: 재시도를 다 소진하면 ConcurrentUpdateFailedException으로 변환된다")
    void extendDeadline_exhaustsRetries_throwsConcurrentUpdateFailed() {
        when(projectRepository.findById(anyLong()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Project.class, 1L));

        assertThatThrownBy(() -> projectService.extendDeadline(1L, new ProjectDeadlineExtendRequest(project.getEndAt().plusDays(10))))
                .isInstanceOf(ConcurrentUpdateFailedException.class);
        verify(projectRepository, times(3)).findById(anyLong());
    }

    @Test
    @DisplayName("extendDeadline: 락 충돌이 아닌 검증 예외는 재시도 없이 원래 타입 그대로 전파된다")
    void extendDeadline_nonLockException_notMasked() {
        when(projectRepository.findById(anyLong())).thenReturn(Optional.of(project));

        LocalDate pastEndAt = project.getEndAt().minusDays(1);
        assertThatThrownBy(() -> projectService.extendDeadline(1L, new ProjectDeadlineExtendRequest(pastEndAt)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("마감일은 현재 마감일 이후로만");
        verify(projectRepository, times(1)).findById(anyLong());
    }

    @Test
    @DisplayName("closeEarly: 락 충돌이 재시도 범위 안에서 풀리면 정상 반영된다")
    void closeEarly_retriesUntilSuccess() {
        when(orderPort.getFundedAmount(anyLong())).thenReturn(project.getGoalAmount());
        when(projectRepository.findById(anyLong()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Project.class, 1L))
                .thenReturn(Optional.of(project));

        ProjectResponse response = projectService.closeEarly(1L);

        assertThat(response.status()).isEqualTo(ProjectStatus.SUCCEEDED.name());
        verify(projectRepository, times(2)).findById(anyLong());
    }

    @Test
    @DisplayName("closeEarly: 재시도를 다 소진하면 ConcurrentUpdateFailedException으로 변환된다")
    void closeEarly_exhaustsRetries_throwsConcurrentUpdateFailed() {
        ReflectionTestUtils.setField(project, "fundedAmount", project.getGoalAmount());
        when(projectRepository.findById(anyLong()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Project.class, 1L));

        assertThatThrownBy(() -> projectService.closeEarly(1L))
                .isInstanceOf(ConcurrentUpdateFailedException.class);
        verify(projectRepository, times(3)).findById(anyLong());
    }

    @Test
    @DisplayName("closeEarly: 목표 미달 예외는 재시도 없이 원래 타입 그대로 전파된다")
    void closeEarly_belowGoal_notMasked() {
        when(orderPort.getFundedAmount(anyLong())).thenReturn(BigDecimal.ZERO);
        when(projectRepository.findById(anyLong())).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> projectService.closeEarly(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("목표 금액을 아직 달성하지 못해");
        verify(projectRepository, times(1)).findById(anyLong());
    }

    @Test
    @DisplayName("closeProjectByDeadline: 락 충돌이 재시도 범위 안에서 풀리면 정상 반영된다")
    void closeProjectByDeadline_retriesUntilSuccess() {
        when(orderPort.getFundedAmount(anyLong())).thenReturn(BigDecimal.ZERO);
        when(projectRepository.findById(anyLong()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Project.class, 1L))
                .thenReturn(Optional.of(project));

        projectService.closeProjectByDeadline(1L);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.FAILED);
        verify(projectRepository, times(2)).findById(anyLong());
    }

    @Test
    @DisplayName("closeProjectByDeadline: 재시도를 다 소진하면 ConcurrentUpdateFailedException으로 변환된다")
    void closeProjectByDeadline_exhaustsRetries_throwsConcurrentUpdateFailed() {
        when(projectRepository.findById(anyLong()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Project.class, 1L));

        assertThatThrownBy(() -> projectService.closeProjectByDeadline(1L))
                .isInstanceOf(ConcurrentUpdateFailedException.class);
        verify(projectRepository, times(3)).findById(anyLong());
    }

    @Test
    @DisplayName("closeProjectByDeadline: 락 충돌이 아닌 검증 예외(진행중 아님)는 재시도 없이 원래 타입 그대로 전파된다")
    void closeProjectByDeadline_nonLockException_notMasked() {
        Project notInProgress = Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        when(orderPort.getFundedAmount(anyLong())).thenReturn(BigDecimal.ZERO);
        when(projectRepository.findById(anyLong())).thenReturn(Optional.of(notInProgress));

        assertThatThrownBy(() -> projectService.closeProjectByDeadline(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("진행중 상태에서만");
        verify(projectRepository, times(1)).findById(anyLong());
    }

    @Test
    @DisplayName("closeProjectByDeadline: 마감 확정에 성공하면 그 프로젝트의 리워드도 비활성화 요청이 간다")
    void closeProjectByDeadline_deactivatesRewards() {
        when(orderPort.getFundedAmount(anyLong())).thenReturn(BigDecimal.ZERO);
        when(projectRepository.findById(anyLong())).thenReturn(Optional.of(project));

        projectService.closeProjectByDeadline(1L);

        verify(rewardService).deactivateAllByProject(1L);
    }

    @Test
    @DisplayName("closeEarly: 조기 마감에 성공하면 그 프로젝트의 리워드도 비활성화 요청이 간다")
    void closeEarly_deactivatesRewards() {
        when(orderPort.getFundedAmount(anyLong())).thenReturn(project.getGoalAmount());
        when(projectRepository.findById(anyLong())).thenReturn(Optional.of(project));

        projectService.closeEarly(1L);

        verify(rewardService).deactivateAllByProject(1L);
    }

    @Test
    @DisplayName("closeProjectByDeadline: 캐시된(오래된) fundedAmount와 다르더라도 판정 직전 동기 조회 값을 기준으로 판정한다")
    void closeProjectByDeadline_usesSyncPulledValueOverStaleCache() {
        project.updateFundedAmount(BigDecimal.ZERO); // 스케줄러가 아직 못 돌린 오래된 캐시값이라고 가정
        when(orderPort.getFundedAmount(1L)).thenReturn(project.getGoalAmount());
        when(projectRepository.findById(anyLong())).thenReturn(Optional.of(project));

        projectService.closeProjectByDeadline(1L);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("closeProjectByDeadline: order-service 동기 조회가 실패하면 판정을 진행하지 않고 그대로 전파된다")
    void closeProjectByDeadline_syncPullFails_notJudged() {
        when(orderPort.getFundedAmount(1L)).thenThrow(new ServiceUnavailableException("주문 서비스 응답 없음"));
        when(projectRepository.findById(anyLong())).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> projectService.closeProjectByDeadline(1L))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("closeEarly: 캐시된(오래된) fundedAmount와 다르더라도 판정 직전 동기 조회 값을 기준으로 판정한다")
    void closeEarly_usesSyncPulledValueOverStaleCache() {
        project.updateFundedAmount(BigDecimal.ZERO);
        when(orderPort.getFundedAmount(1L)).thenReturn(project.getGoalAmount());
        when(projectRepository.findById(anyLong())).thenReturn(Optional.of(project));

        ProjectResponse response = projectService.closeEarly(1L);

        assertThat(response.status()).isEqualTo(ProjectStatus.SUCCEEDED.name());
    }

    @Test
    @DisplayName("closeEarly: order-service 동기 조회가 실패하면 판정을 진행하지 않고 그대로 전파된다")
    void closeEarly_syncPullFails_notJudged() {
        when(orderPort.getFundedAmount(1L)).thenThrow(new ServiceUnavailableException("주문 서비스 응답 없음"));
        when(projectRepository.findById(anyLong())).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> projectService.closeEarly(1L))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("updateFundedAmount: 락 충돌이 재시도 범위(3회) 안에서 풀리면 정상 반영된다")
    void updateFundedAmount_retriesUntilSuccess() {
        when(projectRepository.findById(anyLong()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Project.class, 1L))
                .thenThrow(new ObjectOptimisticLockingFailureException(Project.class, 1L))
                .thenReturn(Optional.of(project));

        projectService.updateFundedAmount(1L, BigDecimal.valueOf(300_000));

        assertThat(project.getFundedAmount()).isEqualByComparingTo(BigDecimal.valueOf(300_000));
        verify(projectRepository, times(3)).findById(anyLong());
    }

    @Test
    @DisplayName("updateFundedAmount: 재시도를 다 소진하면 ConcurrentUpdateFailedException으로 변환된다")
    void updateFundedAmount_exhaustsRetries_throwsConcurrentUpdateFailed() {
        when(projectRepository.findById(anyLong()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Project.class, 1L));

        assertThatThrownBy(() -> projectService.updateFundedAmount(1L, BigDecimal.valueOf(300_000)))
                .isInstanceOf(ConcurrentUpdateFailedException.class);
        verify(projectRepository, times(3)).findById(anyLong());
    }

    @Test
    @DisplayName("updateFundedAmount: 락 충돌이 아닌 검증 예외(음수)는 재시도 없이 원래 타입 그대로 전파된다")
    void updateFundedAmount_negativeAmount_notMasked() {
        when(projectRepository.findById(anyLong())).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> projectService.updateFundedAmount(1L, BigDecimal.valueOf(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("모금액은 0 이상");
        verify(projectRepository, times(1)).findById(anyLong());
    }
}
