package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class ProjectSearchCircuitBreakerConfig {

    static final String PROJECT_SEARCH_ID = "projectSearch";
    static final String PROJECT_AUTOCOMPLETE_ID = "projectAutocomplete";
    static final String PROJECT_EMBEDDING_ID = "projectEmbedding";
    /** 벌크 재색인 전용 서킷브레이커 id (페이지당 최대 50개 프로젝트 임베딩 일괄 생성이므로 180초로 넉넉하게 설정) */
    static final String PROJECT_BULK_INDEX_ID = "projectBulkIndex";

    private final TimeLimiterRegistry timeLimiterRegistry;

    @PostConstruct
    void registerProjectSearchTimeLimiterConfig() {
        timeLimiterRegistry.addConfiguration(PROJECT_SEARCH_ID, TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(10))
                .build());
        timeLimiterRegistry.addConfiguration(PROJECT_AUTOCOMPLETE_ID, TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(800))
                .build());
        timeLimiterRegistry.addConfiguration(PROJECT_EMBEDDING_ID, TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(15))
                .build());
        timeLimiterRegistry.addConfiguration(PROJECT_BULK_INDEX_ID, TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(180))
                .build());
    }

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> projectSearchCircuitBreakerCustomizer() {
        return factory -> factory.configure(builder -> builder
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                    .slidingWindowSize(10)
                    .minimumNumberOfCalls(4)
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofSeconds(10))
                    .permittedNumberOfCallsInHalfOpenState(2)
                    .build()),
                PROJECT_SEARCH_ID, PROJECT_AUTOCOMPLETE_ID, PROJECT_EMBEDDING_ID, PROJECT_BULK_INDEX_ID);
    }
}
