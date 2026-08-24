package com.growmighty.lectures.firstday.project.project.application;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.application.port.FilePort;
import com.growmighty.lectures.firstday.project.project.application.port.OrderPort;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * cancel()은 @Retryable 대상이지만(재시도 자체는 ProjectServiceImplRetryTest에서 검증),
 * 소유권/관리자 판단 로직 자체는 Spring 컨텍스트 없이 순수 Mockito로 충분하다.
 */
class ProjectServiceImplCancelTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectCategoryRepository projectCategoryRepository = mock(ProjectCategoryRepository.class);
    private final RewardService rewardService = mock(RewardService.class);
    private final OrderPort orderPort = mock(OrderPort.class);
    private final ProjectSearchPort searchPort = mock(ProjectSearchPort.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final FilePort filePort = mock(FilePort.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ProjectService> selfProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<RewardService> rewardServiceProvider = mock(ObjectProvider.class);

    private final ProjectServiceImpl projectService =
            new ProjectServiceImpl(projectRepository, projectCategoryRepository, selfProvider, rewardServiceProvider, orderPort, searchPort, eventPublisher, filePort, Clock.systemDefaultZone());

    private Project project;

    @BeforeEach
    void setUp() {
        project = Project.register(OWNER_ID, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        project.approve();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(rewardServiceProvider.getObject()).thenReturn(rewardService);
    }

    @Test
    @DisplayName("본인은 자기 프로젝트를 취소할 수 있다")
    void cancel_byOwner_succeeds() {
        projectService.cancel(1L, OWNER_ID, UserRole.CREATOR);

        assertThat(project.getStatus().name()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("관리자는 남의 프로젝트도 취소할 수 있다")
    void cancel_byAdmin_succeeds() {
        projectService.cancel(1L, OTHER_USER_ID, UserRole.ADMIN);

        assertThat(project.getStatus().name()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("본인도 관리자도 아니면 취소할 수 없다")
    void cancel_byOtherBacker_rejected() {
        assertThatThrownBy(() -> projectService.cancel(1L, OTHER_USER_ID, UserRole.BACKER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("본인이 등록한 프로젝트만");
    }

    @Test
    @DisplayName("취소되면 RewardService에 그 프로젝트의 리워드 비활성화를 위임한다")
    void cancel_deactivatesRewards() {
        projectService.cancel(1L, OWNER_ID, UserRole.CREATOR);

        verify(rewardService).deactivateAllByProject(1L);
    }
}
