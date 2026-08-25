package com.growmighty.lectures.firstday.project.project.application;

import java.util.UUID;

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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectServiceImplReindexTest {

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

    private ProjectServiceImpl projectService;

    @BeforeEach
    void setUp() {
        when(rewardServiceProvider.getObject()).thenReturn(rewardService);
        projectService = new ProjectServiceImpl(
                projectRepository, projectCategoryRepository, selfProvider, rewardServiceProvider, orderPort, searchPort, eventPublisher, filePort, Clock.systemDefaultZone());
    }

    @Test
    @DisplayName("전체 프로젝트를 페이지 단위로 조회해 벌크 색인한다")
    void reindexAllProjects_bulkIndexesEachPage() {
        Project p1 = Project.register(1L, UUID.randomUUID(), null, "title1", 1L, "s", "d",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        Project p2 = Project.register(1L, UUID.randomUUID(), null, "title2", 1L, "s", "d",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        List<Project> projects = List.of(p1, p2);
        when(projectRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(projects, PageRequest.of(0, 50), projects.size()));

        projectService.reindexAllProjects();

        verify(searchPort).bulkIndex(projects);
    }

    @Test
    @DisplayName("프로젝트가 하나도 없으면 bulkIndex를 호출하지 않는다")
    void reindexAllProjects_noProjects_doesNotCallBulkIndex() {
        when(projectRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        projectService.reindexAllProjects();

        verify(searchPort, never()).bulkIndex(any());
    }
}
