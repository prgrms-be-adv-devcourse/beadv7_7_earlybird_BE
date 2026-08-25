package com.growmighty.lectures.firstday.project.project.presentation;

import java.util.UUID;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import com.growmighty.lectures.firstday.project.project.domain.ProjectStatus;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectCreateRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectDeadlineExtendRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectRejectRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** BACKER는 프로젝트를 등록할 수 없고, CREATOR/ADMIN만 가능한지 검증한다. ADMIN이 아니면 관리자 전용 API도 통과하지 못하는지 함께 검증한다. */
class ProjectControllerTest {

    private final ProjectService projectService = mock(ProjectService.class);
    private final ProjectController controller = new ProjectController(projectService);

    private ProjectCreateRequest request() {
        return new ProjectCreateRequest(null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30), UUID.randomUUID());
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

    @Test
    void findAll_passesRequesterRoleThrough() {
        controller.findAll(UserRole.ADMIN, "keyword", 1L, ProjectStatus.PENDING_REVIEW, null);
        verify(projectService).findAll("keyword", 1L, ProjectStatus.PENDING_REVIEW, null, UserRole.ADMIN);
    }

    @Test
    @DisplayName("X-User-Role 헤더가 없으면(비로그인) BACKER로 취급해 공개된 프로젝트만 조회한다")
    void findAll_noRoleHeader_treatsAsBacker() {
        controller.findAll(null, null, null, null, null);
        verify(projectService).findAll(null, null, null, null, UserRole.BACKER);
    }

    @Test
    void approve_nonAdmin_rejected() {
        assertThatThrownBy(() -> controller.approve(UserRole.CREATOR, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(projectService, never()).approve(1L);
    }

    @Test
    void approve_admin_allowed() {
        controller.approve(UserRole.ADMIN, 1L);
        verify(projectService).approve(1L);
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
    void reindex_nonAdmin_rejected() {
        assertThatThrownBy(() -> controller.reindex(UserRole.CREATOR))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(projectService);
    }

    @Test
    void reindex_admin_allowed() {
        controller.reindex(UserRole.ADMIN);
        verify(projectService).reindexAllProjects();
    }
}
