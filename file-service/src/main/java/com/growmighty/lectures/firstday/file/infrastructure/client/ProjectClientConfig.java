package com.growmighty.lectures.firstday.file.infrastructure.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
public class ProjectClientConfig {

    // [기본 빌더] 한정자 없이 RestClient.Builder를 찾는 모든 곳이 받아간다 — Eureka 클라이언트도
    // 자기 등록 요청에 컨텍스트의 빌더 빈을 주워다 쓰므로, 이 @Primary 빌더가 없으면 아래
    // @LoadBalanced 빌더를 받아가 자기 등록 요청까지 로드밸런서를 태우다 죽는다.
    @Bean
    @Primary
    RestClient.Builder plainRestClientBuilder() {
        return RestClient.builder();
    }

    // [LB 빌더] 이 빌더로 만든 RestClient만 URL의 호스트 자리를 "서비스 이름"으로 해석해
    // 요청 시점에 Eureka 명부에서 실제 인스턴스를 골라 치환한다.
    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    RestClient projectRestClient(
            @LoadBalanced RestClient.Builder builder,
            @Value("${file.client.project-base-url:http://project-service}") String baseUrl) {
        return builder.clone().baseUrl(baseUrl).build();
    }
}
