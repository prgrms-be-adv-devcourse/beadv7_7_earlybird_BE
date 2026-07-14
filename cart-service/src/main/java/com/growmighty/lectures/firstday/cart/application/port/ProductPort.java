package com.growmighty.lectures.firstday.cart.application.port;

import com.growmighty.lectures.firstday.cart.application.port.dto.ProductSnapshot;

/**
 * 장바구니가 소유한 상품 조회 계약(Port).
 *
 * <p>cart 는 product 의 클래스를 알지 못한다. 오직 이 인터페이스로만 상품을 바라보고,
 * 실제 통신은 infrastructure 의 HTTP 클라이언트가 담당한다.
 * (order 의 ProductPort 를 재사용하지 않는다 — 각 도메인은 자기 계약을 소유한다.)
 */
public interface ProductPort {
    ProductSnapshot getProduct(Long productId);
}
