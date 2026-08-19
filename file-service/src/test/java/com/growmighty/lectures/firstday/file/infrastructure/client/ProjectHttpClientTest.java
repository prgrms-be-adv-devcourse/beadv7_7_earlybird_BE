package com.growmighty.lectures.firstday.file.infrastructure.client;

import com.growmighty.lectures.firstday.common.exception.BusinessException;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.ConfigBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProjectHttpClientTest {

    // 여기서 검증하려는 건 callGetCreator/failClosed의 예외 분류 로직이지, Resilience4j의
    // ignoreExceptions 배선(FileCircuitBreakerConfig) 자체가 아니다 — 그래서 서킷브레이커는
    // supplier를 그대로 실행하는 최소 stub으로 대체한다.
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final CircuitBreakerFactory PASSTHROUGH_FACTORY = new CircuitBreakerFactory() {
        @Override
        public CircuitBreaker create(String id) {
            return new CircuitBreaker() {
                @Override
                public <T> T run(Supplier<T> toRun, Function<Throwable, T> fallback) {
                    try {
                        return toRun.get();
                    } catch (Exception e) {
                        return fallback.apply(e);
                    }
                }
            };
        }

        @Override
        protected ConfigBuilder configBuilder(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void configureDefault(Function defaultConfiguration) {
        }
    };

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://project-service");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final ProjectHttpClient client = new ProjectHttpClient(builder.build(), PASSTHROUGH_FACTORY);

    @Test
    void 존재하는_프로젝트면_creatorId를_반환한다() {
        server.expect(requestTo("http://project-service/internal/v1/projects/10/creator"))
            .andRespond(withSuccess("""
                {"success": true, "data": {"creatorId": 42}, "error": null}
                """, org.springframework.http.MediaType.APPLICATION_JSON));

        assertThat(client.getCreatorId(10L)).isEqualTo(42L);
    }

    @Test
    void 존재하지_않는_프로젝트면_EntityNotFoundException으로_거부한다() {
        server.expect(requestTo("http://project-service/internal/v1/projects/999/creator"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.getCreatorId(999L))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void project_service_장애면_낙관적으로_통과시키지_않고_503으로_거부한다() {
        server.expect(requestTo("http://project-service/internal/v1/projects/20/creator"))
            .andRespond(withServerError());

        assertThatThrownBy(() -> client.getCreatorId(20L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getStatus())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
