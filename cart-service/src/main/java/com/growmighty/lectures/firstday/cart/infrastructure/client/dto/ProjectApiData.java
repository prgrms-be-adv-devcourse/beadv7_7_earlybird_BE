package com.growmighty.lectures.firstday.cart.infrastructure.client.dto;

import java.math.BigDecimal;

/**
 * project-service 의 GET /projects/{id} 응답 data 부분.
 * 여기서 실제로 쓰는 값은 id/orderable 뿐 — 나머지는 계약 문서화용으로 남긴다.
 * (모르는 JSON 필드는 무시되므로 전체 응답을 다 적을 필요는 없다)
 */
public record ProjectApiData(
        Long id,
        Long creatorId,
        String title,
        BigDecimal goalAmount,
        String status,
        boolean orderable
) {
}
