package com.growmighty.lectures.firstday.project.reward.application;

import java.util.UUID;

import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
import com.growmighty.lectures.firstday.project.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 진짜 MySQL(Testcontainers)에 진짜 스레드로 동시 중복 요청을 재현해, (orderId, rewardId, operation)
 * 멱등성 체크(#195)가 낙관적 락 재시도와 부딪히지 않고 실제로 "딱 한 번만 반영"을 보장하는지 확인한다.
 */
@SpringBootTest
class RewardStockIdempotencyIntegrationTest extends MySqlIntegrationTestSupport {

    @Autowired
    private RewardService rewardService;
    @Autowired
    private RewardRepository rewardRepository;
    @Autowired
    private ProjectRepository projectRepository;

    @Test
    @DisplayName("같은 (orderId, rewardId, DECREASE)로 여러 요청이 동시에 와도 재고는 정확히 1번만 차감된다")
    void sameOrderId_concurrentDuplicateDecreaseStock_appliesOnce() throws InterruptedException {
        Long projectId = publishedProject();
        Long rewardId = reward(projectId, 100);
        long sameOrderId = 999L;

        int duplicateRequests = 5;
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < duplicateRequests; i++) {
            tasks.add(() -> rewardService.decreaseStock(rewardId, 1, sameOrderId));
        }

        Throwable[] results = runAllConcurrently(tasks);

        for (Throwable t : results) {
            assertThat(t).as("멱등성 체크는 예외 없이 조용히 종료돼야 한다").isNull();
        }
        Reward finalReward = rewardRepository.findById(rewardId).orElseThrow();
        assertThat(finalReward.getRemainingQuantity()).isEqualTo(99);
    }

    @Test
    @DisplayName("같은 (orderId, rewardId)에 대해 decrease 후 restore하면 둘 다 반영되어 재고가 원래대로 돌아온다")
    void sameOrderId_decreaseThenRestore_bothApplied() {
        Long projectId = publishedProject();
        Long rewardId = reward(projectId, 100);
        long orderId = 555L;

        rewardService.decreaseStock(rewardId, 3, orderId);
        rewardService.restoreStock(rewardId, 3, orderId);

        Reward finalReward = rewardRepository.findById(rewardId).orElseThrow();
        assertThat(finalReward.getRemainingQuantity()).isEqualTo(100);
    }

    private Long publishedProject() {
        Project project = Project.register(1L, UUID.randomUUID(), null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        project = projectRepository.save(project);
        project.approve();
        project = projectRepository.save(project);
        return project.getProjectId();
    }

    private Long reward(Long projectId, int totalQuantity) {
        Reward reward = Reward.register(projectId, UUID.randomUUID(), "노트커버", "설명", BigDecimal.valueOf(10_000), totalQuantity);
        return rewardRepository.save(reward).getRewardId();
    }
}
