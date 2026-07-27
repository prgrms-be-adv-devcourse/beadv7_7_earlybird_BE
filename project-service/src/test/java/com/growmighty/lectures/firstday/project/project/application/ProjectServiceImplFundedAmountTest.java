package com.growmighty.lectures.firstday.project.project.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.application.port.OrderPort;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * order-service가 push(PUT /internal/v1/projects/{id}/funded-amount)로 호출하는 경로 —
 * @Retryable 대상이 아니라서 ProjectServiceImplDeleteTest와 같은 이유로 순수 Mockito만 쓴다.
 */
class ProjectServiceImplFundedAmountTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectCategoryRepository projectCategoryRepository = mock(ProjectCategoryRepository.class);
    private final RewardService rewardService = mock(RewardService.class);
    private final OrderPort orderPort = mock(OrderPort.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<ProjectService> selfProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<RewardService> rewardServiceProvider = mock(ObjectProvider.class);

    private ProjectServiceImpl projectService;
    private Project project;

    @BeforeEach
    void setUp() {
        when(rewardServiceProvider.getObject()).thenReturn(rewardService);
        projectService = new ProjectServiceImpl(
                projectRepository, projectCategoryRepository, selfProvider, rewardServiceProvider, orderPort);

        project = Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
    }

    @Test
    @DisplayName("전달받은 절대값으로 모금액을 갱신한다")
    void updateFundedAmount_overwritesProjectFundedAmount() {
        projectService.updateFundedAmount(1L, BigDecimal.valueOf(500_000));

        assertThat(project.getFundedAmount()).isEqualByComparingTo(BigDecimal.valueOf(500_000));
    }

    @Test
    @DisplayName("존재하지 않는 프로젝트면 예외를 던진다")
    void updateFundedAmount_projectNotFound_throws() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.updateFundedAmount(99L, BigDecimal.valueOf(500_000)))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
