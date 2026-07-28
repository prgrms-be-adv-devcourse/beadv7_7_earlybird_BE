package com.growmighty.lectures.firstday.project.project.application;

import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.application.port.OrderPort;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectCreateRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectUpdateRequest;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * creatorId를 body가 아니라 헤더에서 받고, update/delete는 요청자가 본인이 등록한
 * 프로젝트인지 검증한다는 auth 관련 변경을 검증한다.
 */
class ProjectServiceImplOwnershipTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectCategoryRepository projectCategoryRepository = mock(ProjectCategoryRepository.class);
    private final RewardService rewardService = mock(RewardService.class);
    private final OrderPort orderPort = mock(OrderPort.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ProjectService> selfProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<RewardService> rewardServiceProvider = mock(ObjectProvider.class);

    private final ProjectServiceImpl projectService =
            new ProjectServiceImpl(projectRepository, projectCategoryRepository, selfProvider, rewardServiceProvider, orderPort);

    private Project project;

    @BeforeEach
    void setUp() {
        project = Project.register(OWNER_ID, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.findByIdForDelete(1L)).thenReturn(Optional.of(project));
        when(projectCategoryRepository.existsById(1L)).thenReturn(true);
        when(rewardServiceProvider.getObject()).thenReturn(rewardService);
        when(orderPort.hasOrderedReward(1L)).thenReturn(false);
    }

    @Test
    @DisplayName("create()는 요청받은 creatorId로 프로젝트를 등록한다")
    void create_usesGivenCreatorId() {
        ProjectCreateRequest request = new ProjectCreateRequest(null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = projectService.create(OWNER_ID, request);

        assertThat(response.creatorId()).isEqualTo(OWNER_ID);
    }

    @Test
    @DisplayName("본인 프로젝트는 수정할 수 있다")
    void update_byOwner_succeeds() {
        ProjectUpdateRequest request = new ProjectUpdateRequest(null, null, "새 요약", null, null, null, null, null);

        var response = projectService.update(1L, OWNER_ID, request);

        assertThat(response.summary()).isEqualTo("새 요약");
    }

    @Test
    @DisplayName("본인이 등록하지 않은 프로젝트는 수정할 수 없다")
    void update_byNonOwner_rejected() {
        ProjectUpdateRequest request = new ProjectUpdateRequest(null, null, "새 요약", null, null, null, null, null);

        assertThatThrownBy(() -> projectService.update(1L, OTHER_USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("본인이 등록한 프로젝트만");
    }

    @Test
    @DisplayName("본인 프로젝트는 삭제할 수 있다 — 리워드를 먼저 지우고 프로젝트를 나중에 지운다")
    void delete_byOwner_succeeds() {
        projectService.delete(1L, OWNER_ID);

        // 순서가 중요하다 — 반대로 바뀌면(Project 먼저 삭제) FK 없는 Reward가 고아로 남는다.
        InOrder order = inOrder(rewardService, projectRepository);
        order.verify(rewardService).deleteAllByProject(1L);
        order.verify(projectRepository).delete(project);
    }

    @Test
    @DisplayName("본인이 등록하지 않은 프로젝트는 삭제할 수 없다")
    void delete_byNonOwner_rejected() {
        assertThatThrownBy(() -> projectService.delete(1L, OTHER_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("본인이 등록한 프로젝트만");

        verify(projectRepository, never()).delete(any(Project.class));
        verify(rewardService, never()).deleteAllByProject(any());
    }
}
