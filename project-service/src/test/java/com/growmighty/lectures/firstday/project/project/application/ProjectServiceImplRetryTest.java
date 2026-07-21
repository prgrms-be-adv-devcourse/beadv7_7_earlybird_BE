package com.growmighty.lectures.firstday.project.project.application;

import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.domain.ProjectStatus;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectDeadlineExtendRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectResponse;
import com.growmighty.lectures.firstday.project.reward.application.exception.ConcurrentUpdateFailedException;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
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
        RewardRepository rewardRepository() {
            return mock(RewardRepository.class);
        }

        @SuppressWarnings("unchecked")
        @Bean
        ObjectProvider<ProjectService> selfProvider() {
            return mock(ObjectProvider.class);
        }

        @Bean
        ProjectService projectService(ProjectRepository projectRepository, ProjectCategoryRepository projectCategoryRepository,
                                       RewardRepository rewardRepository, ObjectProvider<ProjectService> selfProvider) {
            return new ProjectServiceImpl(projectRepository, projectCategoryRepository, rewardRepository, selfProvider);
        }
    }

    @Autowired
    private ProjectService projectService;
    @Autowired
    private ProjectRepository projectRepository;

    private Project project;

    @BeforeEach
    void setUp() {
        reset(projectRepository);
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
        ReflectionTestUtils.setField(project, "fundedAmount", project.getGoalAmount());
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
        when(projectRepository.findById(anyLong())).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> projectService.closeEarly(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("목표 금액을 아직 달성하지 못해");
        verify(projectRepository, times(1)).findById(anyLong());
    }

    @Test
    @DisplayName("closeProjectByDeadline: 락 충돌이 재시도 범위 안에서 풀리면 정상 반영된다")
    void closeProjectByDeadline_retriesUntilSuccess() {
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
        when(projectRepository.findById(anyLong())).thenReturn(Optional.of(notInProgress));

        assertThatThrownBy(() -> projectService.closeProjectByDeadline(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("진행중 상태에서만");
        verify(projectRepository, times(1)).findById(anyLong());
    }
}
