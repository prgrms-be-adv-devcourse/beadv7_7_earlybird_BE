package com.growmighty.lectures.firstday.project.project.application;

import java.util.UUID;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.application.port.FilePort;
import com.growmighty.lectures.firstday.project.project.application.port.OrderPort;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.domain.ProjectSort;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectResponse;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectServiceImplFindAllSearchTest {

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
    @DisplayName("keyword가 없으면 ES를 호출하지 않는다")
    void findAll_noKeyword_doesNotCallSearchPort() {
        when(projectRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        projectService.findAll(null, null, null, null, UserRole.BACKER);

        verify(searchPort, never()).search(any());
    }

    @Test
    @DisplayName("keyword가 있으면 ES 검색 결과로 후보를 좁혀 MySQL에서 최종 조회한다")
    void findAll_withKeyword_routesThroughSearchPort() {
        when(searchPort.search("텀블러")).thenReturn(List.of(1L, 2L));
        when(projectRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        projectService.findAll("텀블러", null, null, null, UserRole.BACKER);

        verify(searchPort).search("텀블러");
    }

    @Test
    @DisplayName("ES 매치가 하나도 없으면 MySQL을 조회하지 않고 즉시 빈 리스트를 반환한다")
    void findAll_noMatches_returnsEmptyWithoutQueryingMySql() {
        when(searchPort.search("존재안함")).thenReturn(List.of());

        List<ProjectResponse> result = projectService.findAll("존재안함", null, null, null, UserRole.BACKER);

        assertThat(result).isEmpty();
        verify(projectRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @DisplayName("ES 검색이 실패하면 폴백 없이 503이 그대로 전파된다")
    void findAll_searchFails_propagatesServiceUnavailable() {
        when(searchPort.search("키워드")).thenThrow(new ServiceUnavailableException("검색 서비스가 일시적으로 응답하지 않습니다."));

        assertThatThrownBy(() -> projectService.findAll("키워드", null, null, null, UserRole.BACKER))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(projectRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @DisplayName("keyword가 있고 sort를 명시하지 않으면 ES가 넘겨준 관련도 순서 그대로 반환한다")
    void findAll_withKeywordNoExplicitSort_preservesEsRelevanceOrder() {
        Project mostRelevant = projectWithId(3L);
        Project leastRelevant = projectWithId(1L);
        Project middle = projectWithId(2L);
        when(searchPort.search("고양이")).thenReturn(List.of(3L, 2L, 1L));
        // DB는 관련도와 무관한 순서로 돌려준다 — 그래도 결과는 ES 순서(3,2,1)를 따라야 한다.
        when(projectRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(leastRelevant, mostRelevant, middle));

        List<ProjectResponse> result = projectService.findAll("고양이", null, null, null, UserRole.BACKER);

        assertThat(result).extracting(ProjectResponse::projectId).containsExactly(3L, 2L, 1L);
        verify(projectRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @DisplayName("keyword가 있어도 sort를 명시하면 관련도 대신 그 정렬 기준을 그대로 쓴다")
    void findAll_withKeywordAndExplicitSort_usesRequestedSortNotRelevance() {
        when(searchPort.search("고양이")).thenReturn(List.of(3L, 2L, 1L));
        when(projectRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        projectService.findAll("고양이", null, null, ProjectSort.FUNDED_AMOUNT, UserRole.BACKER);

        verify(projectRepository).findAll(any(Specification.class), any(Sort.class));
        verify(projectRepository, never()).findAll(any(Specification.class));
    }

    private Project projectWithId(Long projectId) {
        Project project = Project.register(1L, UUID.randomUUID(), null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        ReflectionTestUtils.setField(project, "projectId", projectId);
        return project;
    }
}
