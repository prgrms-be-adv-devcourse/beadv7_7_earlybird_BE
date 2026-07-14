package com.growmighty.lectures.firstday.cart.infrastructure.client;

import com.growmighty.lectures.firstday.cart.application.port.ProductPort;
import com.growmighty.lectures.firstday.cart.application.port.dto.ProductSnapshot;
import com.growmighty.lectures.firstday.cart.infrastructure.client.dto.ApiResponseBody;
import com.growmighty.lectures.firstday.cart.infrastructure.client.dto.ProductApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductHttpClient implements ProductPort {

    private final RestClient productRestClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Override
    public ProductSnapshot getProduct(Long productId) {
        return circuitBreakerFactory.create("product").run(
            () -> callGetProduct(productId),
            cause -> optimisticFallback(productId, cause));
    }

    private ProductSnapshot callGetProduct(Long productId) {
        ApiResponseBody<ProductApiData> body = productRestClient.get()
            .uri("/products/{productId}", productId)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });

        ProductApiData data = body.data();
        return new ProductSnapshot(data.id(), data.orderable());
    }

    private ProductSnapshot optimisticFallback(Long productId, Throwable cause) {
        log.warn("상품 확인 실패 → 낙관적으로 담기 진행. productId={}, 원인={}", productId, cause.toString());
        // 장바구니 담기는 구매가 아니다 — 최종 검증(판매 가능/재고)은 어차피 주문 시점에 다시 한다.
        // 그래서 확인이 안 되면 '판매 중이겠거니' 하고 일단 담아 준다.
        // 사용자는 product-service 가 죽었다는 사실조차 모른다. 이것이 우아한 실패(graceful degradation)다.
        return new ProductSnapshot(productId, true);
    }
}
