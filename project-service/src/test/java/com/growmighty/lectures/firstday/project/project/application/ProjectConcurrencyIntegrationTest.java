package com.growmighty.lectures.firstday.project.project.application;

import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.domain.ProjectStatus;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.exception.ConcurrentUpdateFailedException;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * closeExpiredProjects()의 수동 트리거(POST /close-expired)와 자정 스케줄러가 겹쳐서 같은
 * 프로젝트에 동시에 closeProjectByDeadline()을 호출하는 상황을 진짜 MySQL(Testcontainers)에
 * 진짜 스레드로 재현한다. Project에 @Version이 없었다면 이 경합은 예외 없이 조용히 마지막에
 * 커밋한 쪽 값으로 덮어써질 수 있었다 — @Version 추가 후에는 정확히 한 스레드만 성공하고,
 * 나머지는 재시도 후에도 여전히 IN_PROGRESS가 아니게 된 걸 감지해 깔끔하게 실패해야 한다.
 */
@SpringBootTest
class ProjectConcurrencyIntegrationTest extends MySqlIntegrationTestSupport {

    @Autowired
    private ProjectService projectService;
    @Autowired
    private ProjectRepository projectRepository;

    @Test
    @DisplayName("같은 프로젝트에 closeProjectByDeadline을 동시에 여러 번 호출해도 정확히 한 번만 반영된다")
    void closeProjectByDeadline_concurrentCalls_appliesExactlyOnce() throws InterruptedException {
        Long projectId = publishedProject();
        long versionBefore = projectRepository.findById(projectId).orElseThrow().getVersion();

        int threads = 10;
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> projectService.closeProjectByDeadline(projectId));
        }

        Throwable[] results = runAllConcurrently(tasks);

        int successes = 0;
        for (Throwable t : results) {
            if (t == null) {
                successes++;
            } else {
                // 락 충돌 소진(ConcurrentUpdateFailedException) 또는 이미 처리된 뒤 재조회해서
                // 발견한 "진행중 아님"(IllegalStateException) — 둘 다 정상적인 패자(loser) 경로다.
                assertThat(t).isInstanceOfAny(ConcurrentUpdateFailedException.class, IllegalStateException.class);
            }
        }
        assertThat(successes).as("정확히 한 스레드만 마감 처리에 성공해야 한다").isEqualTo(1);

        Project finalProject = projectRepository.findById(projectId).orElseThrow();
        assertThat(finalProject.getStatus()).isEqualTo(ProjectStatus.FAILED);
        assertThat(finalProject.getVersion()).as("성공한 스레드 수만큼만 버전이 올라가야 한다(lost update 없음)")
                .isEqualTo(versionBefore + 1);
    }

    private Long publishedProject() {
        Project project = Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        project = projectRepository.save(project);
        project.approve();
        project = projectRepository.save(project);
        return project.getProjectId();
    }

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
}
