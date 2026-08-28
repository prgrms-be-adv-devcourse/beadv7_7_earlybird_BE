package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.support.ElasticsearchIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * '노트북' 등 단순 키워드 검색의 동시 다발적 요청 시
 * Virtual Thread Executor 환경에서 ForkJoinPool 스레드 기아 없이
 * 10초 TimeLimiter Timeout 없이 모두 정상 처리되는지 검증하는 동시성 테스트.
 */
@SpringBootTest
class ProjectSearchConcurrencyTest extends ElasticsearchIntegrationTestSupport {

    @Autowired
    private ProjectSearchAdapter adapter;
    @Autowired
    private ProjectRepository projectRepository;

    private final List<Long> savedProjectIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        savedProjectIds.forEach(adapter::remove);
        savedProjectIds.clear();
    }

    @Test
    @DisplayName("동시에 10개의 '노트북' 검색 요청이 들어와도 ForkJoinPool 고갈/10초 Timeout 없이 전원 정상 완료된다")
    void concurrentSearches_withVirtualThreadExecutor_allCompleteSuccessfullyWithoutTimeout() throws Exception {
        // 상품 색인
        Project laptop = Project.register(
                1L, UUID.randomUUID(), null, "초경량 AI 고성능 노트북", 1L,
                "최신 인텔 프로세서 탑재 울트라북 노트북", "개발자와 크리에이터를 위한 고성능 노트북",
                BigDecimal.valueOf(1_500_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        Project saved = projectRepository.save(laptop);
        savedProjectIds.add(saved.getProjectId());
        adapter.index(saved);

        // ES 색인 동기화 확인
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> warmUp = adapter.search("노트북");
            assertThat(warmUp).contains(saved.getProjectId());
        });

        // 10개 동시 검색 격발
        int concurrentCount = 10;
        ExecutorService clientExecutor = Executors.newFixedThreadPool(concurrentCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(concurrentCount);
        List<List<Long>> results = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentCount; i++) {
            clientExecutor.submit(() -> {
                try {
                    startGate.await();
                    long start = System.currentTimeMillis();
                    List<Long> res = adapter.search("노트북");
                    long elapsed = System.currentTimeMillis() - start;
                    results.add(res);
                    // 10초 타임아웃 없이 수백 ms 이내 완료 확인
                    assertThat(elapsed).isLessThan(9000);
                } catch (Throwable t) {
                    failureCount.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        // 동시 출발
        startGate.countDown();
        boolean completed = endGate.await(15, TimeUnit.SECONDS);

        clientExecutor.shutdown();

        assertThat(completed).as("모든 동시 검색 요청이 15초 이내에 완료되어야 한다").isTrue();
        assertThat(failureCount.get()).as("타임아웃이나 예외 실패가 0건이어야 한다").isEqualTo(0);
        assertThat(results).hasSize(concurrentCount);
        for (List<Long> result : results) {
            assertThat(result).contains(saved.getProjectId());
        }
    }
}
