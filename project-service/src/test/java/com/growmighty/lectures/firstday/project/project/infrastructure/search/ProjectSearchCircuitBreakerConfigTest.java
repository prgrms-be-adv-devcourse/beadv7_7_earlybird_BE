package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProjectSearchCircuitBreakerConfig가 "projectSearch" id에 10초 타임아웃을 실제로 적용하는지 검증한다.
 *
 * <p>중요: spring-cloud-circuitbreaker-resilience4j 5.0.2에서
 * {@code Resilience4JCircuitBreakerFactory.create(id)}가 실제로 소비하는 TimeLimiterConfig는
 * {@code Resilience4JConfigBuilder.timeLimiterConfig(...)}가 아니라 순정 resilience4j
 * {@code TimeLimiterRegistry}에 이름으로 등록된 설정이다(자세한 근거는 ProjectSearchCircuitBreakerConfig
 * 클래스 주석 참고) — 그래서 여기서도 {@code TimeLimiterRegistry.getConfiguration("projectSearch")}를 직접
 * 조회해서 우리가 등록한 10초가 실제로 들어가 있는지 확인한다. 이 값은 create()가 실제로 참조하는
 * 소스이므로, 3초/10초를 실제로 기다려 타임아웃 발동을 관찰하지 않고도 "적용됐는지"를 확정할 수 있다.
 */
class ProjectSearchCircuitBreakerConfigTest {

    @Test
    @DisplayName("projectSearch 이름으로 TimeLimiterRegistry에 10초 타임아웃 설정이 등록된다")
    void registersProjectSearchTimeLimiterConfig_withTenSecondTimeout() {
        TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.ofDefaults();
        new ProjectSearchCircuitBreakerConfig(timeLimiterRegistry).registerProjectSearchTimeLimiterConfig();

        assertThat(timeLimiterRegistry.getConfiguration("projectSearch"))
                .as("projectSearch 이름의 TimeLimiterConfig가 등록되어 있어야 한다")
                .isPresent()
                .get()
                .extracting(config -> config.getTimeoutDuration())
                .isEqualTo(Duration.ofSeconds(10));

        assertThat(timeLimiterRegistry.getConfiguration("projectBulkIndex"))
                .as("projectBulkIndex 이름의 TimeLimiterConfig가 등록되어 있어야 한다")
                .isPresent()
                .get()
                .extracting(config -> config.getTimeoutDuration())
                .isEqualTo(Duration.ofSeconds(180));

        // 다른 id는 여전히 이름 붙은 설정이 없다 — 이 등록이 지정된 id에만 좁게 적용됨을 보여준다.
        assertThat(timeLimiterRegistry.getConfiguration("order")).isNotPresent();
    }

    @Test
    @DisplayName("projectSearch 서킷브레이커의 CircuitBreakerConfig도 실패율/슬라이딩윈도우 설정이 실제로 적용된다")
    void projectSearchCircuitBreakerCustomizer_appliesCircuitBreakerConfig() {
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.ofDefaults();
        // bulkheadProvider는 이 테스트에서 쓰지 않는다(create()가 벌크헤드를 건드리지 않음) — null로 충분하다.
        Resilience4JCircuitBreakerFactory factory =
                new Resilience4JCircuitBreakerFactory(circuitBreakerRegistry, timeLimiterRegistry, null);

        new ProjectSearchCircuitBreakerConfig(timeLimiterRegistry)
                .projectSearchCircuitBreakerCustomizer()
                .customize(factory);
        CircuitBreaker circuitBreaker = factory.create("projectSearch");
        // CircuitBreakerRegistry에는 실제 CircuitBreaker가 run() 첫 호출 시점에야 등록된다(지연 등록) —
        // create() 직후 바로 조회하면 우리가 설정한 값이 아니라 레지스트리 기본값을 새로 만들어버린다.
        circuitBreaker.run(() -> "ok", ex -> "fallback:" + ex);

        assertThat(circuitBreakerRegistry.circuitBreaker("projectSearch").getCircuitBreakerConfig()
                .getSlidingWindowSize()).isEqualTo(10);
        assertThat(circuitBreakerRegistry.circuitBreaker("projectSearch").getCircuitBreakerConfig()
                .getFailureRateThreshold()).isEqualTo(50f);
    }
}
