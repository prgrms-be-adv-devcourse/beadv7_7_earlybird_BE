package com.growmighty.lectures.firstday.cart.infrastructure.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
public class CartClientConfig {

    // [기본 빌더] 한정자 없이 RestClient.Builder 를 찾는 모든 곳이 받아 가는 빌더.
    // Boot 4 의 Eureka 클라이언트도 서버(8761)와 통신할 때 컨텍스트의 빌더 빈을 주워다 쓴다.
    // 이 @Primary 빌더가 없으면 Eureka 가 아래 @LoadBalanced 빌더를 받아 가서
    // 자기 "등록" 요청까지 로드밸런서를 태우다 죽는다. (자주 겪는 문제 ④-1)
    @Bean
    @Primary
    RestClient.Builder plainRestClientBuilder() {
        return RestClient.builder();
    }

    // [LB 빌더] 이 빌더로 만든 RestClient 만 URL 의 호스트 자리를 "서비스 이름"으로 해석한다.
    // 요청 시점에 Eureka 명부에서 실제 인스턴스를 골라(라운드로빈) 주소를 치환한다.
    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    RestClient productRestClient(
            @LoadBalanced RestClient.Builder builder,   // 한정자로 LB 빌더를 콕 집어 주입 (빼먹으면 @Primary 가 온다!)
            @Value("${cart.client.product-base-url:http://product-service}") String baseUrl) {
        // builder 는 공유 빈이라 상태가 있다. clone 으로 스냅샷을 떠서 각자 조립한다.
        return builder.clone().baseUrl(baseUrl).build();
    }
}
