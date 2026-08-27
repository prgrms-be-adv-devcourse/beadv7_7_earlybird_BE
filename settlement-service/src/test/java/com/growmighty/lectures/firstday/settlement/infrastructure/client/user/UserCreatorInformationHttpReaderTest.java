package com.growmighty.lectures.firstday.settlement.infrastructure.client.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformation;
import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformationException;
import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformationException.FailureType;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class UserCreatorInformationHttpReaderTest {
    private static final String BASE_URL = "http://user-service";
    private MockRestServiceServer server;
    private UserCreatorInformationHttpReader reader;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        circuitBreaker = CircuitBreaker.of("test", CircuitBreakerConfig.custom().slidingWindowSize(1).minimumNumberOfCalls(1)
                .failureRateThreshold(100).recordException(UserCreatorInformationHttpReaderTest::isAvailabilityFailure).build());
        Retry retry = Retry.of("test", RetryConfig.custom().maxAttempts(1).waitDuration(Duration.ZERO)
                .retryOnException(UserCreatorInformationHttpReaderTest::isAvailabilityFailure).build());
        reader = new UserCreatorInformationHttpReader(builder.build(), retry, circuitBreaker);
    }

    @Test
    void readsCreatorInformationFromExistingInternalUserApi() {
        server.expect(once(), requestTo(BASE_URL + "/internal/v1/users/7")).andRespond(withSuccess("""
                {"success":true,"data":{"id":7,"email":"creator@example.com","name":"창작자","phoneNumber":"01012345678","role":"CREATOR"},"error":null}
                """, MediaType.APPLICATION_JSON));
        CreatorInformation information = reader.read(7L);
        assertThat(information.email()).isEqualTo("creator@example.com");
        assertThat(information.name()).isEqualTo("창작자");
        assertThat(information.phoneNumber()).isEqualTo("01012345678");
        assertThat(information).hasToString("CreatorInformation[REDACTED]");
        server.verify();
    }

    @Test
    void rejectsNonCreatorOrInvalidResponse() {
        server.expect(once(), requestTo(BASE_URL + "/internal/v1/users/7")).andRespond(withSuccess("""
                {"success":true,"data":{"id":7,"email":"backer@example.com","name":"후원자","phoneNumber":"01012345678","role":"BACKER"},"error":null}
                """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> reader.read(7L)).isInstanceOfSatisfying(CreatorInformationException.class,
                exception -> assertThat(exception.failureType()).isEqualTo(FailureType.CONTRACT));
        server.verify();
    }

    @Test
    void rejectsInvalidEnvelope() {
        server.expect(once(), requestTo(BASE_URL + "/internal/v1/users/7")).andRespond(withSuccess("""
                {"success":true,"data":null,"error":null}
                """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> reader.read(7L)).isInstanceOfSatisfying(CreatorInformationException.class,
                exception -> assertThat(exception.failureType()).isEqualTo(FailureType.CONTRACT));
        server.verify();
    }

    @Test
    void classifiesNotFoundAsNonAvailabilityFailure() {
        server.expect(once(), requestTo(BASE_URL + "/internal/v1/users/7")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> reader.read(7L)).isInstanceOfSatisfying(CreatorInformationException.class,
                exception -> assertThat(exception.failureType()).isEqualTo(FailureType.NOT_FOUND));
        server.verify();
    }

    @Test
    void classifiesUserServerFailureAsAvailabilityFailure() {
        server.expect(once(), requestTo(BASE_URL + "/internal/v1/users/7")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> reader.read(7L)).isInstanceOfSatisfying(CreatorInformationException.class,
                exception -> assertThat(exception.failureType()).isEqualTo(FailureType.AVAILABILITY));
        server.verify();
    }

    @Test
    void doesNotCallUserWhenCircuitBreakerIsOpen() {
        circuitBreaker.transitionToForcedOpenState();
        assertThatThrownBy(() -> reader.read(7L)).isInstanceOfSatisfying(CreatorInformationException.class,
                exception -> assertThat(exception.failureType()).isEqualTo(FailureType.AVAILABILITY));
        server.verify();
    }

    private static boolean isAvailabilityFailure(Throwable throwable) {
        return throwable instanceof CreatorInformationException exception && exception.failureType() == FailureType.AVAILABILITY;
    }
}
