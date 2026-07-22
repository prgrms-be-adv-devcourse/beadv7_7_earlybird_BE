package com.growmighty.lectures.firstday.project.reward.application;

import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.reward.application.exception.ConcurrentUpdateFailedException;
import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.request.RewardUpdateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 목킹이 아니라 진짜 MySQL(Testcontainers)에 진짜 스레드를 동시에 꽂아서 낙관적 락(@Version)
 * 경합을 재현한다 — "재시도 로직이 있으니 안전할 것이다"라는 추론이 아니라, 실제로 동시에 때려도
 * 데이터가 안 깨지는지를 관찰로 확인한다.
 *
 * <p>각 시나리오는 서로 다른 두 작업(관리자 decreaseQuantity ↔ 후원자 decreaseStock,
 * 크리에이터 increaseQuantity(update) ↔ 후원자 decreaseStock)을 같은 리워드 행에 동시에
 * 쏟아붓고, 끝난 뒤 "성공한 연산 수만큼만 정확히 반영됐는지"를 최종 DB 상태로 검증한다.
 * lost update가 하나라도 있었다면 이 수식이 어긋난다.
 */
@Testcontainers
@SpringBootTest
class RewardConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private RewardService rewardService;
    @Autowired
    private RewardRepository rewardRepository;
    @Autowired
    private ProjectRepository projectRepository;

    @Test
    @DisplayName("관리자 decreaseQuantity와 후원자 decreaseStock을 동시에 쏟아부어도 최종 수량이 정확하다")
    void decreaseQuantity_vs_decreaseStock_concurrently_staysConsistent() throws InterruptedException {
        Long projectId = publishedProject();
        Long rewardId = reward(projectId, 100);

        int adminThreads = 5;
        int backerThreads = 5;
        List<Runnable> tasks = new java.util.ArrayList<>();
        for (int i = 0; i < adminThreads; i++) {
            tasks.add(() -> rewardService.decreaseQuantity(rewardId, 1));
        }
        for (int i = 0; i < backerThreads; i++) {
            tasks.add(() -> rewardService.decreaseStock(rewardId, 1));
        }

        Throwable[] results = runAllConcurrently(tasks);

        // 실패가 있었다면 오직 재시도 소진(ConcurrentUpdateFailedException)만 허용 — 그 외 타입이면
        // @Recover 매칭이 잘못돼 엉뚱한 예외로 마스킹됐다는 뜻이라 버그다.
        assertNoUnexpectedFailures(results);

        int adminSuccesses = countSuccesses(results, 0, adminThreads);
        int backerSuccesses = countSuccesses(results, adminThreads, tasks.size());

        Reward finalReward = rewardRepository.findById(rewardId).orElseThrow();
        assertThat(finalReward.getTotalQuantity()).isEqualTo(100 - adminSuccesses);
        assertThat(finalReward.getRemainingQuantity()).isEqualTo(finalReward.getTotalQuantity() - backerSuccesses);
    }

    @Test
    @DisplayName("크리에이터 increaseQuantity(update)와 후원자 decreaseStock을 동시에 쏟아부어도 최종 수량이 정확하다")
    void increaseQuantity_vs_decreaseStock_concurrently_staysConsistent() throws InterruptedException {
        Long projectId = publishedProject();
        Long rewardId = reward(projectId, 100);

        int creatorThreads = 5;
        int backerThreads = 5;
        RewardUpdateRequest increaseByOne = new RewardUpdateRequest(null, null, null, null, 1);
        List<Runnable> tasks = new java.util.ArrayList<>();
        for (int i = 0; i < creatorThreads; i++) {
            tasks.add(() -> rewardService.update(rewardId, increaseByOne));
        }
        for (int i = 0; i < backerThreads; i++) {
            tasks.add(() -> rewardService.decreaseStock(rewardId, 1));
        }

        Throwable[] results = runAllConcurrently(tasks);

        assertNoUnexpectedFailures(results);

        int creatorSuccesses = countSuccesses(results, 0, creatorThreads);
        int backerSuccesses = countSuccesses(results, creatorThreads, tasks.size());

        Reward finalReward = rewardRepository.findById(rewardId).orElseThrow();
        assertThat(finalReward.getTotalQuantity()).isEqualTo(100 + creatorSuccesses);
        assertThat(finalReward.getRemainingQuantity()).isEqualTo(finalReward.getTotalQuantity() - backerSuccesses);
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

    /**
     * tasks를 CountDownLatch로 동시에 출발시키고, 각 task의 결과(성공=null, 실패=예외)를 원래
     * 인덱스 그대로 담아 반환한다 — 구간별(관리자/크리에이터 vs 후원자) 성공 개수를 정확히 세려면
     * 어느 태스크가 몇 번째였는지가 보존돼야 하기 때문에 필터링하지 않고 배열째로 돌려준다.
     */
    private Throwable[] runAllConcurrently(List<Runnable> tasks) throws InterruptedException {
        int n = tasks.size();
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        Throwable[] results = new Throwable[n];
        for (int i = 0; i < n; i++) {
            int idx = i;
            Runnable task = tasks.get(i);
            pool.submit(() -> {
                try {
                    start.await();
                    task.run();
                } catch (Throwable t) {
                    results[idx] = t;
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        boolean finished = done.await(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(finished).as("모든 스레드가 제한 시간 안에 끝나야 한다").isTrue();
        return results;
    }

    private void assertNoUnexpectedFailures(Throwable[] results) {
        for (Throwable t : results) {
            if (t != null) {
                assertThat(t).isInstanceOf(ConcurrentUpdateFailedException.class);
            }
        }
    }

    private int countSuccesses(Throwable[] results, int fromIndex, int toIndex) {
        int successes = 0;
        for (int i = fromIndex; i < toIndex; i++) {
            if (results[i] == null) {
                successes++;
            }
        }
        return successes;
    }
}
