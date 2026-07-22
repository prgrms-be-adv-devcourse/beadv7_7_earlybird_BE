package com.growmighty.lectures.firstday.project.project.presentation;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import com.growmighty.lectures.firstday.project.project.domain.ProjectStatus;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectDeadlineExtendRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectRejectRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** ADMIN이 아니면 어떤 관리자 API도 project-service까지 통과하지 못하는지 검증한다. */
class ProjectAdminControllerTest {

    private final ProjectService projectService = mock(ProjectService.class);
    private final ProjectAdminController controller = new ProjectAdminController(projectService);

    @Test
    void findByStatus_nonAdmin_rejected() {
        assertThatThrownBy(() -> controller.findByStatus(UserRole.BACKER, ProjectStatus.PENDING_REVIEW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("관리자만");
        verifyNoInteractions(projectService);
    }

    @Test
    void approve_nonAdmin_rejected() {
        assertThatThrownBy(() -> controller.approve(UserRole.CREATOR, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(projectService, never()).approve(1L);
    }

    @Test
    void reject_nonAdmin_rejected() {
        assertThatThrownBy(() -> controller.reject(UserRole.BACKER, 1L, new ProjectRejectRequest("사유")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(projectService);
    }

    @Test
    void extendDeadline_nonAdmin_rejected() {
        assertThatThrownBy(() -> controller.extendDeadline(UserRole.BACKER, 1L, new ProjectDeadlineExtendRequest(LocalDate.now().plusDays(1))))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(projectService);
    }

    @Test
    void closeExpiredProjects_nonAdmin_rejected() {
        assertThatThrownBy(() -> controller.closeExpiredProjects(UserRole.BACKER))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(projectService);
    }

    @Test
    void closeEarly_nonAdmin_rejected() {
        assertThatThrownBy(() -> controller.closeEarly(UserRole.CREATOR, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(projectService);
    }

    @Test
    void admin_isAllowedThrough() {
        controller.findByStatus(UserRole.ADMIN, ProjectStatus.PENDING_REVIEW);
        verify(projectService).findByStatus(ProjectStatus.PENDING_REVIEW);
    }
}
