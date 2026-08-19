package com.growmighty.lectures.firstday.project.project.application;

import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.application.port.FilePort;
import com.growmighty.lectures.firstday.project.project.application.port.OrderPort;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSuggestion;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectServiceImplAutocompleteTest {

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
                projectRepository, projectCategoryRepository, selfProvider, rewardServiceProvider, orderPort, searchPort, eventPublisher, filePort);
    }

    /** Project.register()는 항상 PENDING_REVIEW(비공개)로 시작한다 — approve() 호출 시 IN_PROGRESS(공개)로 전환. */
    private Project project(Long projectId, String title, boolean published) {
        Project project = Project.register(1L, null, title, 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        ReflectionTestUtils.setField(project, "projectId", projectId);
        if (published) {
            project.approve();
        }
        return project;
    }

    @Test
    @DisplayName("공개된 프로젝트만 DB 기준 제목으로 반환한다")
    void autocomplete_returnsPublishedProjectsWithDbTitle() {
        when(searchPort.autocomplete("카카")).thenReturn(List.of(new ProjectSuggestion(1L, "카카오 프로젝트(ES 구버전 제목)")));
        Project published = project(1L, "카카오 프로젝트(최신 제목)", true);
        when(projectRepository.findAllById(List.of(1L))).thenReturn(List.of(published));

        List<ProjectSuggestion> result = projectService.autocomplete("카카");

        assertThat(result).containsExactly(new ProjectSuggestion(1L, "카카오 프로젝트(최신 제목)"));
    }

    @Test
    @DisplayName("심사 대기/반려 상태의 후보는 결과에서 제외된다")
    void autocomplete_filtersOutUnpublishedCandidates() {
        when(searchPort.autocomplete("카카")).thenReturn(List.of(
                new ProjectSuggestion(1L, "카카오 프로젝트"),
                new ProjectSuggestion(2L, "카카오 심사대기"),
                new ProjectSuggestion(3L, "카카오 반려됨")));

        Project published = project(1L, "카카오 프로젝트", true);
        Project pendingReview = project(2L, "카카오 심사대기", false);
        Project rejected = project(3L, "카카오 반려됨", false);
        rejected.reject("사유");
        when(projectRepository.findAllById(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(published, pendingReview, rejected));

        List<ProjectSuggestion> result = projectService.autocomplete("카카");

        assertThat(result).containsExactly(new ProjectSuggestion(1L, "카카오 프로젝트"));
    }

    @Test
    @DisplayName("결과는 최대 10개로 제한되고 projectId 오름차순으로 정렬된다")
    void autocomplete_limitsToTenResultsSortedByProjectId() {
        List<ProjectSuggestion> candidates = java.util.stream.LongStream.rangeClosed(1, 12)
                .mapToObj(id -> new ProjectSuggestion(id, "프로젝트" + id))
                .toList();
        when(searchPort.autocomplete("프로젝트")).thenReturn(candidates);

        // 일부러 역순으로 반환해서, 서비스가 projectId 기준으로 다시 정렬함을 검증한다.
        List<Project> publishedDescending = java.util.stream.LongStream.rangeClosed(1, 12)
                .boxed()
                .sorted(java.util.Comparator.reverseOrder())
                .map(id -> project(id, "프로젝트" + id, true))
                .toList();
        when(projectRepository.findAllById(any())).thenReturn(publishedDescending);

        List<ProjectSuggestion> result = projectService.autocomplete("프로젝트");

        assertThat(result).hasSize(10);
        assertThat(result).extracting(ProjectSuggestion::projectId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
    }

    @Test
    @DisplayName("ES 후보가 비어있으면 DB 조회 없이 빈 결과를 반환한다")
    void autocomplete_emptyCandidates_returnsEmptyWithoutQueryingDb() {
        when(searchPort.autocomplete("존재안함")).thenReturn(List.of());

        List<ProjectSuggestion> result = projectService.autocomplete("존재안함");

        assertThat(result).isEmpty();
        verify(projectRepository, never()).findAllById(anyList());
    }

    @Test
    @DisplayName("ES 장애 시 폴백 없이 503이 그대로 전파된다")
    void autocomplete_searchFails_propagatesServiceUnavailable() {
        when(searchPort.autocomplete("카카")).thenThrow(new ServiceUnavailableException("검색 서비스가 일시적으로 응답하지 않습니다."));

        assertThatThrownBy(() -> projectService.autocomplete("카카"))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}
