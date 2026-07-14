package com.growmighty.lectures.firstday.order.infrastructure.client;

import com.growmighty.lectures.firstday.order.application.port.ProductPort;
import com.growmighty.lectures.firstday.order.application.port.dto.ProductSnapshot;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.ApiResponseBody;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.ProductApiData;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.StockChangeBody;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// Feign 프록시가 곧 ProductPort 구현체다 — 별도 어댑터(ProductHttpClient) 없이 포트를 직접 구현한다.
// name = Eureka 전화번호부에 올라간 이름 (상대의 spring.application.name). URL 이 아니다!
// 호출 시점마다 내장 로드밸런서가 명부에서 실제 주소를 찾아 치환한다.
@FeignClient(name = "product-service")
public interface ProductFeignClient extends ProductPort {

    // ── 실제 HTTP 계약 (Feign 이 프록시로 구현) ──────────────────────────
    // product-service 의 ProductController 시그니처를 거울처럼 비춰 적는다.
    // 응답 봉투(ApiResponseBody)와 API DTO(ProductApiData)는 HTTP 세계의 언어라 여기 남긴다.
    @GetMapping("/products/{productId}")
    ApiResponseBody<ProductApiData> fetchProduct(@PathVariable("productId") Long productId);

    @PostMapping("/products/{productId}/decrease-stock")
    void sendDecreaseStock(@PathVariable("productId") Long productId, @RequestBody StockChangeBody body);

    @PostMapping("/products/{productId}/restore-stock")
    void sendRestoreStock(@PathVariable("productId") Long productId, @RequestBody StockChangeBody body);

    // ── 포트 구현 (default 메서드 = 순수 자바, HTTP 프록시가 아니다) ──────
    // 봉투 벗기기 + "API DTO → 도메인 언어" 번역(ACL)을 여기서 한다.
    // 이 번역 계층 덕에 product 의 응답 형태가 바뀌어도 여파가 이 파일에서 멈춘다.
    @Override
    default ProductSnapshot getProduct(Long productId) {
        ProductApiData data = fetchProduct(productId).data();
        return new ProductSnapshot(
                data.id(),
                data.name(),
                data.price(),
                data.stockQuantity(),
                data.orderable());
    }

    @Override
    default void decreaseStock(Long productId, int quantity) {
        sendDecreaseStock(productId, new StockChangeBody(quantity));
    }

    @Override
    default void restoreStock(Long productId, int quantity) {
        sendRestoreStock(productId, new StockChangeBody(quantity));
    }
}
