package com.growmighty.lectures.firstday.project.project.application;

import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.application.port.OrderPort;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectCreateRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectUpdateRequest;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** create/update/delete가 색인을 정확한 지점에서 호출하는지만 검증한다(Mockito, Spring 컨텍스트 불필요). */
class ProjectServiceImplSearchIndexingTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectCategoryRepository projectCategoryRepository = mock(ProjectCategoryRepository.class);
    private final RewardService rewardService = mock(RewardService.class);
    private final OrderPort orderPort = mock(OrderPort.class);
    private final ProjectSearchPort searchPort = mock(ProjectSearchPort.class);

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
                projectRepository, projectCategoryRepository, selfProvider, rewardServiceProvider, orderPort, searchPort);
        when(selfProvider.getObject()).thenReturn(projectService);
        project = Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
    }

    @Test
    @DisplayName("프로젝트 생성 시 색인한다")
    void create_indexesProject() {
        ProjectCreateRequest request = new ProjectCreateRequest(1L, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        when(projectCategoryRepository.existsById(1L)).thenReturn(true);
        when(projectRepository.save(any(Project.class))).thenReturn(project);

        projectService.create(1L, request);

        verify(searchPort).index(project);
    }

    @Test
    @DisplayName("프로젝트 수정 시 재색인한다")
    void update_reindexesProject() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        ProjectUpdateRequest request = new ProjectUpdateRequest(
                null, null, "new summary", null, null, null, null, null);

        projectService.update(1L, 1L, request);

        verify(searchPort).index(project);
    }

    @Test
    @DisplayName("프로젝트 삭제 시 색인에서도 제거한다")
    void delete_removesFromIndex() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.findByIdForDelete(1L)).thenReturn(Optional.of(project));
        when(orderPort.hasOrderedReward(1L)).thenReturn(false);

        projectService.delete(1L, 1L);

        verify(searchPort).remove(1L);
    }
}
