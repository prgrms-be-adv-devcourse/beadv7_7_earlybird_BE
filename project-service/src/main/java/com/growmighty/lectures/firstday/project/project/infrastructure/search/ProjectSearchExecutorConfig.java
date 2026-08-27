package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 프로젝트 검색 파이프라인(BM25, Embedding, 5개 kNN 필드 병렬 조회) 전용 Executor 설정.
 *
 * <p>Spring Boot의 {@code spring.threads.virtual.enabled: true} 설정에 맞춰 Virtual Thread 기반
 * Executor를 구성하여, Blocking I/O(OpenAI, Elasticsearch) 및 중첩 CompletableFuture 환경에서
 * ForkJoinPool.commonPool() worker 스레드 경합(Thread Starvation)을 방지한다.
 */
@Configuration
public class ProjectSearchExecutorConfig {

    public static final String SEARCH_TASK_EXECUTOR_BEAN = "searchTaskExecutor";

    @Bean(name = SEARCH_TASK_EXECUTOR_BEAN)
    public Executor searchTaskExecutor(@Value("${spring.threads.virtual.enabled:false}") boolean virtualThreadsEnabled) {
        if (virtualThreadsEnabled) {
            SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("search-vt-");
            executor.setVirtualThreads(true);
            return executor;
        }

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(16);
        executor.setMaxPoolSize(64);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("search-pt-");
        executor.initialize();
        return executor;
    }
}
