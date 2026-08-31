package com.growmighty.lectures.firstday.project.project.application;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.project.category.domain.ProjectCategory;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.application.port.OrderPort;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectResponse;
import com.growmighty.lectures.firstday.project.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 카테고리 필터(?categoryId=)가 하위 카테고리 프로젝트까지 잡는지 진짜 MySQL로 확인한다(#761).
 * 프로젝트는 리프 카테고리에 매달리므로, 상위 카테고리 필터가 정확 일치였을 땐 항상 빈 목록이었다.
 */
@SpringBootTest
class ProjectCategoryFilterIntegrationTest extends MySqlIntegrationTestSupport {

    @Autowired
    private ProjectService projectService;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectCategoryRepository categoryRepository;
    @MockitoBean
    private OrderPort orderPort;

    @Test
    @DisplayName("상위 카테고리로 필터하면 하위 카테고리에 달린 프로젝트도 나온다")
    void findAll_상위_카테고리_필터_하위_포함() {
        Long root = saveCategory(null, "패션-" + UUID.randomUUID());
        Long mid = saveCategory(root, "의류-" + UUID.randomUUID());
        Long leaf = saveCategory(mid, "상의-" + UUID.randomUUID());
        Long otherRoot = saveCategory(null, "도서-" + UUID.randomUUID());
        Long projectId = publishedProject(leaf);

        assertThat(findAllIds(root)).as("루트 필터").contains(projectId);
        assertThat(findAllIds(mid)).as("중분류 필터").contains(projectId);
        assertThat(findAllIds(leaf)).as("리프 필터(기존 동작)").contains(projectId);
        assertThat(findAllIds(otherRoot)).as("무관한 카테고리 필터").doesNotContain(projectId);
        assertThat(findAllIds(-1L)).as("존재하지 않는 카테고리 필터").doesNotContain(projectId);
    }

    private List<Long> findAllIds(Long categoryId) {
        return projectService.findAll(null, categoryId, null, null, UserRole.BACKER).stream()
                .map(ProjectResponse::projectId)
                .toList();
    }

    private Long saveCategory(Long parentId, String name) {
        return categoryRepository.save(ProjectCategory.create(parentId, name)).getId();
    }

    private Long publishedProject(Long categoryId) {
        Project project = Project.register(1L, UUID.randomUUID(), null, "title", categoryId, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        project = projectRepository.save(project);
        project.approve();
        return projectRepository.save(project).getProjectId();
    }
}
