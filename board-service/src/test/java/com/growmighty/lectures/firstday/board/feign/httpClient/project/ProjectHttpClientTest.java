package com.growmighty.lectures.firstday.board.feign.httpClient.project;

import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import feign.FeignException;
import feign.Request;
import feign.Response;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

// UserHttpClientTest/OrderHttpClientTest와 같은 원칙: Feign 통신 자체는 mock으로 끊고, 우리가 짠 서킷브레이커 배선만 검증한다.
@ExtendWith(MockitoExtension.class)
class ProjectHttpClientTest {

    @Mock
    private ProjectFeignClient projectFeignClient;

    private ProjectHttpClient projectHttpClient;

    @BeforeEach
    void setUp() {
        Resilience4JCircuitBreakerFactory circuitBreakerFactory = new Resilience4JCircuitBreakerFactory(
                CircuitBreakerRegistry.ofDefaults(), TimeLimiterRegistry.ofDefaults(), null);
        projectHttpClient = new ProjectHttpClient(projectFeignClient, circuitBreakerFactory);
    }

    @Test
    @DisplayName("정상 응답이면 존재하는 것으로 판단한다")
    void existsProject_success() {
        boolean result = projectHttpClient.existsProject(1L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("404(FeignException.NotFound)는 장애가 아니라 '존재하지 않음'으로 판단한다")
    void existsProject_notFound_returnsFalse() {
        doThrow(notFoundException()).when(projectFeignClient).getProject(1L);

        boolean result = projectHttpClient.existsProject(1L);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Feign 호출 자체가 실패하면 낙관적으로 넘어가지 않고 ServiceUnavailableException으로 하드 실패한다")
    void existsProject_feignFailure_failsHardViaFallback() {
        doThrow(new RuntimeException("project-service 응답 없음")).when(projectFeignClient).getProject(1L);

        assertThatThrownBy(() -> projectHttpClient.existsProject(1L))
            .isInstanceOf(ServiceUnavailableException.class);
    }

    private FeignException notFoundException() {
        Request request = Request.create(Request.HttpMethod.GET, "/api/v1/projects/1",
                Map.of(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder().status(404).reason("Not Found").request(request).build();
        return FeignException.errorStatus("ProjectFeignClient#getProject", response);
    }
}