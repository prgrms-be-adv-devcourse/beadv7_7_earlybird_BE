package com.growmighty.lectures.firstday.settlement.infrastructure.client.user;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformation;
import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformationException;
import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformationException.FailureType;
import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformationReader;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public final class UserCreatorInformationHttpReader implements CreatorInformationReader {

    static final String CREATOR_INFORMATION_PATH = "/internal/v1/users/{creatorId}";
    private final RestClient restClient;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public UserCreatorInformationHttpReader(RestClient restClient, Retry retry, CircuitBreaker circuitBreaker) {
        this.restClient = Objects.requireNonNull(restClient, "User HTTP 클라이언트는 필수입니다.");
        this.retry = Objects.requireNonNull(retry, "User 정보 Retry는 필수입니다.");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "User 정보 Circuit Breaker는 필수입니다.");
    }

    @Override
    public CreatorInformation read(Long creatorId) {
        if (creatorId == null || creatorId <= 0) {
            throw new IllegalArgumentException("창작자 식별자는 양수여야 합니다.");
        }
        Supplier<CreatorInformation> retrying = Retry.decorateSupplier(retry, () -> request(creatorId));
        try {
            return CircuitBreaker.decorateSupplier(circuitBreaker, retrying).get();
        } catch (CallNotPermittedException exception) {
            throw availabilityFailure("User 창작자 정보 조회가 일시적으로 차단되었습니다.", exception);
        }
    }

    private CreatorInformation request(Long creatorId) {
        try {
            ApiResponse<UserResponse> response = restClient.get().uri(CREATOR_INFORMATION_PATH, creatorId).retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null || !response.success() || response.data() == null || response.error() != null
                    || !creatorId.equals(response.data().id()) || !"CREATOR".equals(response.data().role())) {
                throw contractFailure("User 창작자 정보 응답이 올바르지 않습니다.", null);
            }
            return new CreatorInformation(response.data().email(), response.data().name(), response.data().phoneNumber());
        } catch (RestClientResponseException exception) {
            throw responseFailure(exception);
        } catch (ResourceAccessException exception) {
            throw availabilityFailure("User 서버에 연결할 수 없습니다.", exception);
        } catch (CreatorInformationException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw contractFailure("User 창작자 정보 응답 형식이 올바르지 않습니다.", exception);
        }
    }

    private static CreatorInformationException responseFailure(RestClientResponseException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == HttpStatus.NOT_FOUND) {
            return new CreatorInformationException(FailureType.NOT_FOUND, "User 창작자를 찾을 수 없습니다.", exception);
        }
        if (exception.getStatusCode().is5xxServerError() || status == HttpStatus.TOO_MANY_REQUESTS) {
            return availabilityFailure("User 서버를 일시적으로 사용할 수 없습니다.", exception);
        }
        return contractFailure("User 창작자 정보 조회 요청 또는 응답이 올바르지 않습니다.", exception);
    }

    private static CreatorInformationException availabilityFailure(String message, Throwable cause) {
        return new CreatorInformationException(FailureType.AVAILABILITY, message, cause);
    }

    private static CreatorInformationException contractFailure(String message, Throwable cause) {
        return new CreatorInformationException(FailureType.CONTRACT, message, cause);
    }

    private record UserResponse(Long id, String email, String name, String phoneNumber, String role) { }
}
