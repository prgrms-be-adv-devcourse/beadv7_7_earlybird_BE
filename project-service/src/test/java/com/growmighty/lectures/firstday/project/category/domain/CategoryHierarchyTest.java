package com.growmighty.lectures.firstday.project.category.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryHierarchyTest {

    /** 패션(1) > 의류(2) > 상의(3), 패션(1) > 신발(4), 도서(5) */
    private final CategoryHierarchy hierarchy = CategoryHierarchy.of(List.of(
            category(1L, null, "패션"),
            category(2L, 1L, "의류"),
            category(3L, 2L, "상의"),
            category(4L, 1L, "신발"),
            category(5L, null, "도서")));

    @Test
    @DisplayName("path는 루트부터 자기 자신까지 조상 전체를 붙인다")
    void path_전체_조상_체인() {
        assertThat(hierarchy.path(3L)).isEqualTo("패션 > 의류 > 상의");
        assertThat(hierarchy.path(1L)).isEqualTo("패션");
        assertThat(hierarchy.path(999L)).isEmpty();
        assertThat(hierarchy.path(null)).isEmpty();
    }

    @Test
    @DisplayName("withDescendants는 하위 트리 전체를 포함한다")
    void withDescendants_하위_전체() {
        assertThat(hierarchy.withDescendants(List.of(1L))).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
        assertThat(hierarchy.withDescendants(List.of(3L))).containsExactly(3L);
        assertThat(hierarchy.withDescendants(List.of(999L))).containsExactly(999L);
    }

    @Test
    @DisplayName("rootId는 조상 체인 최상단을 돌려준다")
    void rootId_최상단() {
        assertThat(hierarchy.rootId(3L)).isEqualTo(1L);
        assertThat(hierarchy.rootId(1L)).isEqualTo(1L);
        assertThat(hierarchy.rootId(999L)).isEqualTo(999L);
    }

    @Test
    @DisplayName("부모 순환 참조가 있어도 무한루프에 빠지지 않는다")
    void 순환_참조_방어() {
        CategoryHierarchy cyclic = CategoryHierarchy.of(List.of(
                category(1L, 2L, "A"),
                category(2L, 1L, "B")));

        assertThat(cyclic.path(1L)).isEqualTo("B > A");
        assertThat(cyclic.withDescendants(List.of(1L))).containsExactlyInAnyOrder(1L, 2L);
        assertThat(cyclic.rootId(1L)).isEqualTo(2L);
    }

    private ProjectCategory category(Long id, Long parentId, String name) {
        ProjectCategory category = ProjectCategory.create(parentId, name);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
