package com.growmighty.lectures.firstday.project.category.application;

import com.growmighty.lectures.firstday.project.category.domain.ProjectCategory;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 하위 카테고리나 참조하는 프로젝트가 있으면 삭제를 거부하는지 검증한다(참조무결성). */
class ProjectCategoryServiceImplDeleteTest {

    private final ProjectCategoryRepository projectCategoryRepository = mock(ProjectCategoryRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ProjectCategoryService> selfProvider = mock(ObjectProvider.class);

    private ProjectCategoryServiceImpl projectCategoryService;
    private ProjectCategory category;

    @BeforeEach
    void setUp() {
        projectCategoryService = new ProjectCategoryServiceImpl(projectCategoryRepository, projectRepository, selfProvider);
        category = ProjectCategory.create(null, "전자기기");
        when(projectCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
    }

    @Test
    @DisplayName("하위 카테고리가 있으면 삭제를 거부한다")
    void delete_hasChildCategory_rejected() {
        when(projectCategoryRepository.existsByParentProjectCategoryId(1L)).thenReturn(true);

        assertThatThrownBy(() -> projectCategoryService.deleteTransactional(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("하위 카테고리");

        verify(projectCategoryRepository, never()).delete(category);
    }

    @Test
    @DisplayName("이 카테고리를 쓰는 프로젝트가 있으면 삭제를 거부한다")
    void delete_usedByProject_rejected() {
        when(projectCategoryRepository.existsByParentProjectCategoryId(1L)).thenReturn(false);
        when(projectRepository.existsByCategoryId(1L)).thenReturn(true);

        assertThatThrownBy(() -> projectCategoryService.deleteTransactional(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("사용 중인 프로젝트");

        verify(projectCategoryRepository, never()).delete(category);
    }

    @Test
    @DisplayName("하위 카테고리도 없고 참조하는 프로젝트도 없으면 정상 삭제된다")
    void delete_noReferences_deletesCategory() {
        when(projectCategoryRepository.existsByParentProjectCategoryId(1L)).thenReturn(false);
        when(projectRepository.existsByCategoryId(1L)).thenReturn(false);

        projectCategoryService.deleteTransactional(1L);

        verify(projectCategoryRepository).delete(category);
    }
}
