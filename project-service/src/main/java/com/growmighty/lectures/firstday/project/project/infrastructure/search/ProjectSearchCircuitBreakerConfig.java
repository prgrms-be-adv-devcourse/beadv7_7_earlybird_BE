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

/**
 * "projectSearch" 서킷브레이커 전용 설정. 이 id에 별도 설정을 안 주면
 * {@code OrderCircuitBreakerConfig.defaultCircuitBreakerCustomizer()}가 만드는 기본값(3초 타임아웃 "의도")을
 * 물려받는다 — 그 3초는 order-service(클러스터 내부 Feign 호출)에 맞춰 튜닝된 값인데, OpenAI 임베딩
 * API(외부 TLS 라운드트립) + ES 쿼리를 함께 담아야 하는 이 호출에는 너무 빠듯하다.
 *
 * <p><b>중요 — 실측으로 확인한 라이브러리 동작:</b> spring-cloud-circuitbreaker-resilience4j 5.0.2에서
 * {@code Resilience4JCircuitBreakerFactory.create(id)}가 실제로 사용하는 타임아웃은
 * {@code Resilience4JConfigBuilder.timeLimiterConfig(...)}(={@code configureDefault}/{@code configure}로
 * 세팅하는 값)이 아니라, 순수 resilience4j {@link TimeLimiterRegistry#getConfiguration(String)}로 조회되는
 * "이름 붙은(named)" 설정이다(없으면 {@code TimeLimiterRegistry}의 전역 기본값 1초로 떨어진다) —
 * 바이트코드 추적 + 실제 슬립 기반 타임아웃 테스트로 직접 확인함: {@code OrderCircuitBreakerConfig}가
 * {@code .timeLimiterConfig(3초)}를 세팅해도 order 서킷브레이커는 실제로는 ~1초 만에 타임아웃되고,
 * {@link TimeLimiterRegistry#addConfiguration(String, TimeLimiterConfig)}로 이름을 등록하면 그 값이 그대로
 * 적용된다. 즉 {@code CircuitBreakerConfig}(실패율/슬라이딩윈도우 등)는 {@code Resilience4JConfigBuilder}
 * 경로로 정상 적용되지만, {@code TimeLimiterConfig}(타임아웃)만큼은 이 경로가 아니라 여기서처럼
 * {@code TimeLimiterRegistry}에 직접 등록해야 실제로 반영된다. (order-service의 3초 설정이 같은
 * 이유로 실제로는 적용되지 않고 있다는 뜻이지만, 그건 이 파일이 다루는 범위 밖이라 손대지 않는다 —
 * 팀에 별도 공유 필요.)
 */
@Configuration
@RequiredArgsConstructor
public class ProjectSearchCircuitBreakerConfig {

    static final String PROJECT_SEARCH_ID = "projectSearch";

    private final TimeLimiterRegistry timeLimiterRegistry;

    /** TimeLimiterConfig(타임아웃)는 이 이름 붙은 설정 등록을 통해서만 실제로 적용된다(위 클래스 설명 참고). */
    @PostConstruct
    void registerProjectSearchTimeLimiterConfig() {
        timeLimiterRegistry.addConfiguration(PROJECT_SEARCH_ID, TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(10))
                .build());
    }

    /**
     * {@link org.springframework.cloud.client.circuitbreaker.AbstractCircuitBreakerFactory#configure}로
     * "projectSearch" id 하나만 골라 CircuitBreakerConfig(실패율/슬라이딩윈도우 등)를 준다 — 이 경로는
     * 실제로 적용됨이 확인됐다. 다른 id(예: "order")는 영향받지 않는다.
     */
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
                PROJECT_SEARCH_ID);
    }
}
