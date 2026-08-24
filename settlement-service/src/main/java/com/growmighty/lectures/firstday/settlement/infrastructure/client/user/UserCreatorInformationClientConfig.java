package com.growmighty.lectures.firstday.settlement.infrastructure.client.user;

import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformationException;
import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformationReader;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(UserCreatorInformationClientProperties.class)
public class UserCreatorInformationClientConfig {

    @Bean
    public RestClient creatorInformationRestClient(
            @LoadBalanced RestClient.Builder builder,
            UserCreatorInformationClientProperties properties
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder.clone().baseUrl(properties.baseUrl().toString()).requestFactory(requestFactory).build();
    }

    @Bean
    public Retry creatorInformationRetry() {
        return Retry.of("creatorInformationRetry", RetryConfig.custom()
                .maxAttempts(2).waitDuration(Duration.ofMillis(200))
                .retryOnException(UserCreatorInformationClientConfig::isAvailabilityFailure).build());
    }

    @Bean
    public CircuitBreaker creatorInformationCircuitBreaker() {
        return CircuitBreaker.of("creatorInformationCircuitBreaker", CircuitBreakerConfig.custom()
                .slidingWindowSize(10).minimumNumberOfCalls(5).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30)).permittedNumberOfCallsInHalfOpenState(1)
                .recordException(UserCreatorInformationClientConfig::isAvailabilityFailure).build());
    }

    @Bean
    public CreatorInformationReader creatorInformationReader(
            @Qualifier("creatorInformationRestClient") RestClient restClient,
            @Qualifier("creatorInformationRetry") Retry retry,
            @Qualifier("creatorInformationCircuitBreaker") CircuitBreaker circuitBreaker
    ) {
        return new UserCreatorInformationHttpReader(restClient, retry, circuitBreaker);
    }

    private static boolean isAvailabilityFailure(Throwable throwable) {
        return throwable instanceof CreatorInformationException exception
                && exception.failureType() == CreatorInformationException.FailureType.AVAILABILITY;
    }
}
