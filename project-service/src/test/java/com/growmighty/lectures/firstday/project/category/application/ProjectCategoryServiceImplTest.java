package com.growmighty.lectures.firstday.project.category.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.project.category.domain.ProjectCategory;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.category.presentation.dto.request.ProjectCategoryCreateRequest;
import com.growmighty.lectures.firstday.project.category.presentation.dto.request.ProjectCategoryUpdateRequest;
import com.growmighty.lectures.firstday.project.category.presentation.dto.response.ProjectCategoryResponse;
import com.growmighty.lectures.firstday.project.category.presentation.dto.response.ProjectCategoryTreeResponse;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** create/findById/findAllAsTree/updateTransactional의 정상 동작과 검증 로직을 확인한다(동시성/삭제는 별도 테스트). */
class ProjectCategoryServiceImplTest {

    private final ProjectCategoryRepository projectCategoryRepository = mock(ProjectCategoryRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ProjectCategoryService> selfProvider = mock(ObjectProvider.class);

    private final ProjectCategoryServiceImpl service =
            new ProjectCategoryServiceImpl(projectCategoryRepository, projectRepository, selfProvider);

    @Test
    @DisplayName("상위 카테고리 없이(루트) 생성할 수 있다")
    void create_root_succeeds() {
        ProjectCategoryCreateRequest request = new ProjectCategoryCreateRequest(null, "전자기기");
        when(projectCategoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectCategoryResponse response = service.create(request);

        assertThat(response.name()).isEqualTo("전자기기");
        assertThat(response.parentProjectCategoryId()).isNull();
    }

    @Test
    @DisplayName("존재하는 상위 카테고리 아래에 생성할 수 있다")
    void create_withExistingParent_succeeds() {
        ProjectCategoryCreateRequest request = new ProjectCategoryCreateRequest(1L, "스마트기기");
        when(projectCategoryRepository.existsById(1L)).thenReturn(true);
        when(projectCategoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectCategoryResponse response = service.create(request);

        assertThat(response.parentProjectCategoryId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("존재하지 않는 상위 카테고리를 지정하면 생성이 거부된다")
    void create_withNonexistentParent_rejected() {
        ProjectCategoryCreateRequest request = new ProjectCategoryCreateRequest(999L, "스마트기기");
        when(projectCategoryRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("존재하는 카테고리를 id로 조회할 수 있다")
    void findById_existing_returnsResponse() {
        ProjectCategory category = ProjectCategory.create(null, "전자기기");
        when(projectCategoryRepository.findById(1L)).thenReturn(Optional.of(category));

        ProjectCategoryResponse response = service.findById(1L);

        assertThat(response.name()).isEqualTo("전자기기");
    }

    @Test
    @DisplayName("존재하지 않는 카테고리를 조회하면 예외가 발생한다")
    void findById_notFound_throws() {
        when(projectCategoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("부모-자식 관계를 트리 구조로 반환한다")
    void findAllAsTree_buildsNestedTree() {
        ProjectCategory root = mock(ProjectCategory.class);
        when(root.getId()).thenReturn(1L);
        when(root.getParentProjectCategoryId()).thenReturn(null);
        when(root.getName()).thenReturn("전자기기");
        when(root.isRoot()).thenReturn(true);

        ProjectCategory child = mock(ProjectCategory.class);
        when(child.getId()).thenReturn(2L);
        when(child.getParentProjectCategoryId()).thenReturn(1L);
        when(child.getName()).thenReturn("스마트기기");
        when(child.isRoot()).thenReturn(false);

        ProjectCategory grandchild = mock(ProjectCategory.class);
        when(grandchild.getId()).thenReturn(3L);
        when(grandchild.getParentProjectCategoryId()).thenReturn(2L);
        when(grandchild.getName()).thenReturn("이어폰");
        when(grandchild.isRoot()).thenReturn(false);

        when(projectCategoryRepository.findAll()).thenReturn(List.of(root, child, grandchild));

        List<ProjectCategoryTreeResponse> tree = service.findAllAsTree();

        assertThat(tree).hasSize(1);
        ProjectCategoryTreeResponse rootResponse = tree.get(0);
        assertThat(rootResponse.id()).isEqualTo(1L);
        assertThat(rootResponse.children()).hasSize(1);
        ProjectCategoryTreeResponse childResponse = rootResponse.children().get(0);
        assertThat(childResponse.id()).isEqualTo(2L);
        assertThat(childResponse.children()).hasSize(1);
        assertThat(childResponse.children().get(0).id()).isEqualTo(3L);
    }

    @Test
    @DisplayName("카테고리가 하나도 없으면 빈 리스트를 반환한다")
    void findAllAsTree_empty_returnsEmptyList() {
        when(projectCategoryRepository.findAll()).thenReturn(List.of());

        assertThat(service.findAllAsTree()).isEmpty();
    }

    @Test
    @DisplayName("이름만 바꾸는 수정은 부모를 그대로 둔다")
    void updateTransactional_renameOnly_succeeds() {
        ProjectCategory category = ProjectCategory.create(1L, "전자기기");
        when(projectCategoryRepository.findById(10L)).thenReturn(Optional.of(category));

        ProjectCategoryResponse response = service.updateTransactional(10L,
                new ProjectCategoryUpdateRequest(1L, "가전제품"));

        assertThat(response.name()).isEqualTo("가전제품");
        assertThat(response.parentProjectCategoryId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("존재하는 다른 카테고리로 부모를 바꿀 수 있다")
    void updateTransactional_changeToValidParent_succeeds() {
        ProjectCategory category = ProjectCategory.create(null, "전자기기");
        when(projectCategoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(projectCategoryRepository.existsById(5L)).thenReturn(true);
        when(projectCategoryRepository.findById(5L)).thenReturn(Optional.of(ProjectCategory.create(null, "가전")));

        ProjectCategoryResponse response = service.updateTransactional(10L,
                new ProjectCategoryUpdateRequest(5L, "전자기기"));

        assertThat(response.parentProjectCategoryId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("자기 자신을 부모로 설정할 수 없다")
    void updateTransactional_selfAsParent_rejected() {
        ProjectCategory category = ProjectCategory.create(null, "전자기기");
        when(projectCategoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(projectCategoryRepository.existsById(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateTransactional(10L,
                new ProjectCategoryUpdateRequest(10L, "전자기기")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("자기 자신");
    }

    @Test
    @DisplayName("자손 카테고리를 부모로 설정할 수 없다")
    void updateTransactional_descendantAsParent_rejected() {
        // 10(부모) - 20(자식). 10의 부모를 20으로 바꾸려 하면 순환이 생기므로 거부돼야 한다.
        ProjectCategory parent = ProjectCategory.create(null, "전자기기");
        ProjectCategory descendant = ProjectCategory.create(10L, "스마트기기");
        when(projectCategoryRepository.findById(10L)).thenReturn(Optional.of(parent));
        when(projectCategoryRepository.existsById(20L)).thenReturn(true);
        when(projectCategoryRepository.findById(20L)).thenReturn(Optional.of(descendant));

        assertThatThrownBy(() -> service.updateTransactional(10L,
                new ProjectCategoryUpdateRequest(20L, "전자기기")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("자손");
    }

    @Test
    @DisplayName("존재하지 않는 카테고리로 부모를 바꾸려 하면 거부된다")
    void updateTransactional_nonexistentParent_rejected() {
        ProjectCategory category = ProjectCategory.create(null, "전자기기");
        when(projectCategoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(projectCategoryRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.updateTransactional(10L,
                new ProjectCategoryUpdateRequest(999L, "전자기기")))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
