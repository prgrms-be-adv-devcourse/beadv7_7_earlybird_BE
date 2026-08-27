package com.growmighty.lectures.firstday.project.project.application;

import java.util.UUID;

import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.application.port.OrderPort;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.domain.ProjectStatus;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * reconcileFundedAmounts()는 @Retryable 대상이 아니라(루프 자체는 재시도 없음, 프로젝트 한 건씩만
 * self.updateFundedAmount()로 재시도) ProjectServiceImplDeleteTest와 같은 이유로 Spring 컨텍스트 없이
 * 순수 Mockito로 검증한다. selfProvider가 이 테스트 인스턴스 자신을 돌려주게 해서 self-invocation을
 * 흉내낸다 — 여기서는 @Retryable 발동 자체(AOP 프록시)가 아니라 루프의 예외 격리만 확인하면 된다.
 * Project.projectId는 @GeneratedValue라 실제 저장 없이는 항상 null이므로, 프로젝트별로 구분되는
 * ID가 필요한 이 테스트에서는 ReflectionTestUtils로 직접 주입한다(ProjectTest의 fundedAmount
 * 주입과 같은 이유).
 */
class ProjectServiceImplReconciliationTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectCategoryRepository projectCategoryRepository = mock(ProjectCategoryRepository.class);
    private final RewardService rewardService = mock(RewardService.class);
    private final OrderPort orderPort = mock(OrderPort.class);
    private final ProjectSearchPort searchPort = mock(ProjectSearchPort.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<ProjectService> selfProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<RewardService> rewardServiceProvider = mock(ObjectProvider.class);

    private ProjectServiceImpl projectService;

    private Project project(Long projectId) {
        Project project = Project.register(1L, UUID.randomUUID(), null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        project.approve();
        ReflectionTestUtils.setField(project, "projectId", projectId);
        return project;
    }

    @BeforeEach
    void setUp() {
        when(rewardServiceProvider.getObject()).thenReturn(rewardService);
        projectService = new ProjectServiceImpl(
                projectRepository, projectCategoryRepository, selfProvider, rewardServiceProvider, orderPort, searchPort, eventPublisher, Clock.systemDefaultZone());
        when(selfProvider.getObject()).thenReturn(projectService);
    }

    @Test
    @DisplayName("IN_PROGRESS 프로젝트마다 order-service를 조회해 fundedAmount를 갱신한다")
    void reconcileFundedAmounts_updatesEachInProgressProject() {
        Project p1 = project(1L);
        Project p2 = project(2L);
        when(projectRepository.findByStatus(ProjectStatus.IN_PROGRESS)).thenReturn(List.of(p1, p2));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(p1));
        when(projectRepository.findById(2L)).thenReturn(Optional.of(p2));
        when(orderPort.getFundedAmount(1L)).thenReturn(BigDecimal.valueOf(100_000));
        when(orderPort.getFundedAmount(2L)).thenReturn(BigDecimal.valueOf(200_000));

        projectService.reconcileFundedAmounts();

        assertThat(p1.getFundedAmount()).isEqualByComparingTo(BigDecimal.valueOf(100_000));
        assertThat(p2.getFundedAmount()).isEqualByComparingTo(BigDecimal.valueOf(200_000));
    }

    @Test
    @DisplayName("한 프로젝트의 order-service 조회가 실패해도 나머지 프로젝트는 계속 갱신된다")
    void reconcileFundedAmounts_onePartialFailure_othersStillUpdated() {
        Project failing = project(1L);
        Project succeeding = project(2L);
        when(projectRepository.findByStatus(ProjectStatus.IN_PROGRESS)).thenReturn(List.of(failing, succeeding));
        when(projectRepository.findById(2L)).thenReturn(Optional.of(succeeding));
        when(orderPort.getFundedAmount(1L)).thenThrow(new ServiceUnavailableException("주문 서비스 응답 없음"));
        when(orderPort.getFundedAmount(2L)).thenReturn(BigDecimal.valueOf(200_000));

        assertThatCode(() -> projectService.reconcileFundedAmounts()).doesNotThrowAnyException();

        assertThat(succeeding.getFundedAmount()).isEqualByComparingTo(BigDecimal.valueOf(200_000));
        assertThat(failing.getFundedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
