package com.growmighty.lectures.firstday.project.project.application;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * delete()는 @Retryable 대상이 아니라서 ProjectServiceImplRetryTest와 달리
 * Spring 컨텍스트 없이 순수 Mockito만으로 검증한다.
 */
class ProjectServiceImplDeleteTest {

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
    private Project project;

    @BeforeEach
    void setUp() {
        when(rewardServiceProvider.getObject()).thenReturn(rewardService);
        projectService = new ProjectServiceImpl(
                projectRepository, projectCategoryRepository, selfProvider, rewardServiceProvider, orderPort, searchPort, eventPublisher, filePort);
        when(selfProvider.getObject()).thenReturn(projectService);

        project = Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.findByIdForDelete(1L)).thenReturn(Optional.of(project));
    }

    @Test
    @DisplayName("후원(주문) 이력이 없으면 정상 삭제된다")
    void delete_noOrders_deletesProject() {
        when(orderPort.hasOrderedReward(1L)).thenReturn(false);

        projectService.delete(1L, 1L);

        verify(rewardService).deleteAllByProject(1L);
        verify(projectRepository).delete(project);
        verify(filePort).deleteProjectFiles(1L);
    }

    @Test
    @DisplayName("후원(주문) 이력이 있으면 삭제를 거부하고 아무것도 지우지 않는다")
    void delete_hasOrders_rejectsDeletion() {
        when(orderPort.hasOrderedReward(1L)).thenReturn(true);

        assertThatThrownBy(() -> projectService.delete(1L, 1L))
                .isInstanceOf(IllegalStateException.class);

        verify(rewardService, never()).deleteAllByProject(anyLong());
        verify(projectRepository, never()).delete(any(Project.class));
        verify(filePort, never()).deleteProjectFiles(anyLong());
    }

    @Test
    @DisplayName("사전 체크(락 밖) 통과 후 배타 락을 잡은 사이 주문이 들어와도 재확인에 걸려 삭제되지 않는다")
    void delete_orderArrivesBetweenPreCheckAndLock_rejectsDeletion() {
        // delete()의 사전 체크 시점엔 주문이 없었지만, deleteInternal()이 배타 락을 잡은 뒤
        // 재확인하는 시점엔 그 사이 decreaseStock()이 끝나 주문이 생긴 상황을 흉내낸다.
        when(orderPort.hasOrderedReward(1L)).thenReturn(false).thenReturn(true);

        assertThatThrownBy(() -> projectService.delete(1L, 1L))
                .isInstanceOf(IllegalStateException.class);

        verify(rewardService, never()).deleteAllByProject(anyLong());
        verify(projectRepository, never()).delete(any(Project.class));
        verify(filePort, never()).deleteProjectFiles(anyLong());
    }
}
