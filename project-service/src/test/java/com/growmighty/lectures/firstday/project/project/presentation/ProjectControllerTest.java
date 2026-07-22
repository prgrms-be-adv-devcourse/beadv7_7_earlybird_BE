package com.growmighty.lectures.firstday.project.project.presentation;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectCreateRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** BACKER는 프로젝트를 등록할 수 없고, CREATOR/ADMIN만 가능한지 검증한다. */
class ProjectControllerTest {

    private final ProjectService projectService = mock(ProjectService.class);
    private final ProjectController controller = new ProjectController(projectService);

    private ProjectCreateRequest request() {
        return new ProjectCreateRequest(null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
    }

    @Test
    void create_backer_rejected() {
        assertThatThrownBy(() -> controller.create(1L, UserRole.BACKER, request()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("창작자 또는 관리자만");
        verifyNoInteractions(projectService);
    }

    @Test
    void create_creator_allowed() {
        ProjectCreateRequest request = request();
        controller.create(1L, UserRole.CREATOR, request);
        verify(projectService).create(1L, request);
    }

    @Test
    void create_admin_allowed() {
        ProjectCreateRequest request = request();
        controller.create(1L, UserRole.ADMIN, request);
        verify(projectService).create(1L, request);
    }
}
