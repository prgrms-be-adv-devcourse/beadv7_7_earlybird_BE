package com.growmighty.lectures.firstday.order.presentation;

import com.growmighty.lectures.firstday.order.application.OrderApiService;
import com.growmighty.lectures.firstday.order.presentation.dto.OrderConsistencyResponse;
import com.growmighty.lectures.firstday.order.presentation.dto.OrderInspectionRequest;
import com.growmighty.lectures.firstday.order.presentation.dto.OrderInspectionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/orders")
public class InternalOrderController {

    private final OrderApiService orderApiService;

    /** 후원 시 결제 검증용 정보 호출. TODO(예정): payment와 연동 — 인증 도입 후 검증, 상세 기능 추후 구현 예정 */
    @GetMapping("/{orderId}/inspect")
    public OrderConsistencyResponse inspectOrder(@PathVariable UUID orderId) {
        return OrderConsistencyResponse.from(orderApiService.inspectOrder(orderId));
    }

    /** 후원 시 결제 정보 검증 저장. TODO(예정): payment와 연동 — 인증 도입 후 검증, 상세 기능 추후 구현 예정 */
    @PostMapping("/inspect/save")
    public OrderInspectionResponse placeOrderInspection(@Valid @RequestBody OrderInspectionRequest request) {
        return OrderInspectionResponse.from(orderApiService.placeOrderInspection(request.orderId()));
    }

    /** 후원 시 결제 검증용 정보 호출. TODO(예정): payment와 연동 — 인증 도입 후 검증, 상세 기능 추후 구현 예정 */
    @GetMapping("/{projectId}/ordered-existence")
    public boolean hasOrderedReward(@PathVariable Long projectId) {
        return orderApiService.hasOrderedReward(projectId);
    }
}
