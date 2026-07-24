package com.growmighty.lectures.firstday.project.category.application;

import com.growmighty.lectures.firstday.project.category.domain.ProjectCategory;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.category.presentation.dto.request.ProjectCategoryCreateRequest;
import com.growmighty.lectures.firstday.project.category.presentation.dto.request.ProjectCategoryUpdateRequest;
import com.growmighty.lectures.firstday.project.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 두 카테고리가 동시에 서로를 부모로 지정하려는 상황(A→B, B→A)을 진짜 스레드로 재현한다.
 * update()의 JVM 락이 없었다면 두 트랜잭션이 서로 상대방의 커밋 전 상태를 보고 둘 다
 * 순환참조 검증을 통과해버려, DB엔 저장되지만 findAllAsTree()로는 영원히 조회 안 되는
 * "증발한" 카테고리가 생길 수 있었다. 락 추가 후에는 정확히 한 스레드만 성공하고,
 * 나머지 한 스레드는 상대방이 이미 커밋한 최신 상태를 보고 순환을 감지해 깔끔하게 실패해야 한다.
 */
@SpringBootTest
class ProjectCategoryConcurrencyIntegrationTest extends MySqlIntegrationTestSupport {

    @Autowired
    private ProjectCategoryService projectCategoryService;
    @Autowired
    private ProjectCategoryRepository projectCategoryRepository;

    @Test
    @DisplayName("두 카테고리가 동시에 서로를 부모로 지정해도 순환참조가 생기지 않는다")
    void update_concurrentMutualParenting_neverCreatesCycle() throws InterruptedException {
        Long categoryAId = createRoot("A");
        Long categoryBId = createRoot("B");

        List<Runnable> tasks = List.of(
                () -> projectCategoryService.update(categoryAId,
                        new ProjectCategoryUpdateRequest(categoryBId, "A")),
                () -> projectCategoryService.update(categoryBId,
                        new ProjectCategoryUpdateRequest(categoryAId, "B")));

        Throwable[] results = runAllConcurrently(tasks);

        int successes = 0;
        for (Throwable t : results) {
            if (t == null) {
                successes++;
            } else {
                assertThat(t).isInstanceOf(IllegalArgumentException.class);
            }
        }
        assertThat(successes).as("정확히 한 스레드만 부모 변경에 성공해야 한다").isEqualTo(1);

        ProjectCategory categoryA = projectCategoryRepository.findById(categoryAId).orElseThrow();
        ProjectCategory categoryB = projectCategoryRepository.findById(categoryBId).orElseThrow();
        boolean aIsParentOfB = categoryBId.equals(categoryA.getParentProjectCategoryId());
        boolean bIsParentOfA = categoryAId.equals(categoryB.getParentProjectCategoryId());
        assertThat(aIsParentOfB ^ bIsParentOfA)
                .as("둘 중 하나만 부모-자식 관계가 되어야 하고, 서로가 서로의 부모인 순환은 생기면 안 된다")
                .isTrue();
    }

    private Long createRoot(String name) {
        return projectCategoryService.create(new ProjectCategoryCreateRequest(null, name)).id();
    }
}
