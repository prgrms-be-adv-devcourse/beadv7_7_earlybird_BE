package com.growmighty.lectures.firstday.project.category.presentation;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.project.category.application.ProjectCategoryService;
import com.growmighty.lectures.firstday.project.category.presentation.dto.request.ProjectCategoryCreateRequest;
import com.growmighty.lectures.firstday.project.category.presentation.dto.request.ProjectCategoryUpdateRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** ADMIN이 아니면 카테고리 생성/수정이 project-service까지 통과하지 못하는지 검증한다. */
class ProjectCategoryControllerTest {

    private final ProjectCategoryService projectCategoryService = mock(ProjectCategoryService.class);
    private final ProjectCategoryController controller = new ProjectCategoryController(projectCategoryService);

    @Test
    void create_nonAdmin_rejected() {
        ProjectCategoryCreateRequest request = new ProjectCategoryCreateRequest(null, "전자기기");

        assertThatThrownBy(() -> controller.create(UserRole.CREATOR, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("관리자만");
        verifyNoInteractions(projectCategoryService);
    }

    @Test
    void create_admin_allowed() {
        ProjectCategoryCreateRequest request = new ProjectCategoryCreateRequest(null, "전자기기");

        controller.create(UserRole.ADMIN, request);

        verify(projectCategoryService).create(request);
    }

    @Test
    void update_nonAdmin_rejected() {
        ProjectCategoryUpdateRequest request = new ProjectCategoryUpdateRequest(null, "가전");

        assertThatThrownBy(() -> controller.update(UserRole.BACKER, 1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("관리자만");
        verifyNoInteractions(projectCategoryService);
    }

    @Test
    void update_admin_allowed() {
        ProjectCategoryUpdateRequest request = new ProjectCategoryUpdateRequest(null, "가전");

        controller.update(UserRole.ADMIN, 1L, request);

        verify(projectCategoryService).update(1L, request);
    }
}
