package com.growmighty.lectures.firstday.project.project.application;

import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.application.port.OrderPort;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSuggestion;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectServiceImplAutocompleteTest {

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

    @BeforeEach
    void setUp() {
        when(rewardServiceProvider.getObject()).thenReturn(rewardService);
        projectService = new ProjectServiceImpl(
                projectRepository, projectCategoryRepository, selfProvider, rewardServiceProvider, orderPort, searchPort);
    }

    @Test
    @DisplayName("검색 포트가 반환한 자동완성 후보를 그대로 전달한다")
    void autocomplete_delegatesToSearchPort() {
        when(searchPort.autocomplete("카카")).thenReturn(List.of(new ProjectSuggestion(1L, "카카오 프로젝트")));

        List<ProjectSuggestion> result = projectService.autocomplete("카카");

        assertThat(result).containsExactly(new ProjectSuggestion(1L, "카카오 프로젝트"));
    }

    @Test
    @DisplayName("ES 장애 시 폴백 없이 503이 그대로 전파된다")
    void autocomplete_searchFails_propagatesServiceUnavailable() {
        when(searchPort.autocomplete("카카")).thenThrow(new ServiceUnavailableException("검색 서비스가 일시적으로 응답하지 않습니다."));

        assertThatThrownBy(() -> projectService.autocomplete("카카"))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}
