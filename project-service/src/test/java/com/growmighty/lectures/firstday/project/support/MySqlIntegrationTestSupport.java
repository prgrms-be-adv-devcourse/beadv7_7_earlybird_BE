package com.growmighty.lectures.firstday.project.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시성 통합 테스트 공용 MySQL 컨테이너 (Testcontainers).
 *
 * <p>싱글턴 컨테이너 패턴: 여러 통합 테스트 클래스가 동일한 Spring 컨텍스트를 공유하므로,
 * 컨테이너도 JVM 당 하나를 띄워 클래스 사이에서 재시작되지 않게 한다.
 * (@Container 방식은 클래스마다 컨테이너를 재시작해 캐시된 컨텍스트가 죽은 포트를 바라보게 된다.)
 * 종료는 Testcontainers(Ryuk)가 JVM 종료 시 정리한다.
 *
 * <p>settlement-service의 MySqlIntegrationTestSupport와 동일한 패턴.
 */
public abstract class MySqlIntegrationTestSupport {

    // 로컬 docker-compose 와 동일한 버전을 쓴다.
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    static {
        MYSQL.start();
    }

    /** 여러 작업을 동시에 시작시켜(CountDownLatch로 출발선 맞춤) 각 스레드의 성공/실패(예외)를 모은다. */
    protected Throwable[] runAllConcurrently(List<Runnable> tasks) throws InterruptedException {
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
