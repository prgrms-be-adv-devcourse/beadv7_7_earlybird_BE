package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 리랭커 관련 Bean 구성. Cohere 관련 Bean({@link RestClient}, {@link CohereRerankClient})은
 * {@code cohere.rerank.enabled=true}일 때만 생성된다 — {@code enabled=false} 환경은 이 Bean도
 * {@code COHERE_API_KEY}도 필요 없이 {@link NoOpReranker}만 활성.
 */
@Configuration
@EnableConfigurationProperties(CohereRerankProperties.class)
public class RerankConfig {

    @Bean
    @ConditionalOnProperty(prefix = "cohere.rerank", name = "enabled", havingValue = "true")
    RestClient cohereRestClient(CohereRerankProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.min(props.timeoutMs(), 2000)));
        factory.setReadTimeout(Duration.ofMillis(props.timeoutMs()));
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .requestFactory(factory)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "cohere.rerank", name = "enabled", havingValue = "true")
    CohereRerankClient cohereRerankClient(RestClient cohereRestClient, CohereRerankProperties props) {
        return new CohereRerankClient(cohereRestClient, props);
    }
}
