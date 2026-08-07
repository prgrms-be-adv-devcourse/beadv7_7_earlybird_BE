package com.growmighty.lectures.firstday.project.project.application;

import com.growmighty.lectures.firstday.project.project.application.port.OrderPort;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
import com.growmighty.lectures.firstday.project.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * delete()는 예전엔 무락으로 Project를 읽고 Reward를 먼저 지운 뒤 Project를 나중에 지웠다.
 * decreaseStock() 같은 Reward 쓰기 경로는 반대로 findByIdForStatusCheck()로 Project를 공유
 * 락으로 먼저 잠그고 Reward를 나중에(커밋 시점) 잠근다. 두 트랜잭션이 겹치면 서로 상대가 쥔
 * 락을 기다리는 순환 대기가 생겨 InnoDB가 데드락으로 하나를 강제 종료시킬 수 있었다.
 * findByIdForDelete()(배타 락)로 delete()가 Project를 맨 먼저 선점하도록 고친 뒤에는 두 경로
 * 모두 Project를 먼저 잠그는 순서로 통일돼 순환 대기가 생기지 않는다 — 목이 아니라 진짜
 * MySQL(Testcontainers)에 진짜 스레드로 동시에 부딪혀서 확인한다.
 */
@SpringBootTest
class ProjectDeleteConcurrencyIntegrationTest extends MySqlIntegrationTestSupport {

    @Autowired
    private ProjectService projectService;
    @Autowired
    private RewardService rewardService;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private RewardRepository rewardRepository;

    @MockitoBean
    private OrderPort orderPort;

    @Test
    @DisplayName("delete()와 decreaseStock()을 동시에 쏟아부어도 데드락 없이 끝난다")
    void delete_vs_decreaseStock_concurrently_noDeadlock() throws InterruptedException {
        when(orderPort.hasOrderedReward(anyLong())).thenReturn(false);

        Long projectId = publishedProject();
        Long rewardId = reward(projectId, 100);

        int backerThreads = 9;
        List<Runnable> tasks = new ArrayList<>();
        tasks.add(() -> projectService.delete(projectId, 1L));
        for (int i = 0; i < backerThreads; i++) {
            long orderId = i; // Task 2/Step 11과 동일한 이유 — 스레드별 고유 orderId 필요
            tasks.add(() -> rewardService.decreaseStock(rewardId, 1, orderId));
        }

        Throwable[] results = runAllConcurrently(tasks);

        for (Throwable t : results) {
            if (t != null) {
                assertThat(t)
                        .as("데드락(CannotAcquireLockException)이 발생하면 락 순서 수정이 원복된 것이다")
                        .isNotInstanceOf(CannotAcquireLockException.class);
            }
        }
    }

    private Long publishedProject() {
        Project project = Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        project = projectRepository.save(project);
        project.approve();
        project = projectRepository.save(project);
        return project.getProjectId();
    }

    private Long reward(Long projectId, int totalQuantity) {
        Reward reward = Reward.register(projectId, "노트커버", "설명", BigDecimal.valueOf(10_000), totalQuantity);
        return rewardRepository.save(reward).getRewardId();
    }
}
