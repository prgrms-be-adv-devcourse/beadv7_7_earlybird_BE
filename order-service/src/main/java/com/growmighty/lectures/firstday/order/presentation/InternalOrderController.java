package com.growmighty.lectures.firstday.order.presentation;

import com.growmighty.lectures.firstday.order.application.OrderApiService;
import com.growmighty.lectures.firstday.order.presentation.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/orders")
public class InternalOrderController {

    private final OrderApiService orderApiService;

    /** 후원 시 결제 검증용 정보 호출. TODO(팀): payment와 연동 — 인증 도입 후 검증, 상세 기능 추후 구현 예정 */
    @GetMapping("/{orderId}/inspect")
    public OrderConsistencyResponse inspectOrder(@PathVariable Long orderId) {
        return OrderConsistencyResponse.from(orderApiService.inspectOrder(orderId));
    }

    /** 후원 시 결제 정보 검증 저장. TODO(팀): payment와 연동 — 인증 도입 후 검증, 상세 기능 추후 구현 예정 */
    @PostMapping("/inspect/save")
    public OrderInspectionResponse placeOrderInspection(@Valid @RequestBody OrderInspectionRequest request) {
        return OrderInspectionResponse.from(orderApiService.placeOrderInspection(request.orderId()));
    }

    /** 후원 시 결제 검증용 정보 호출. TODO(팀): payment와 연동 — 인증 도입 후 검증, 상세 기능 추후 구현 예정 */
    @GetMapping("/{projectId}/ordered-existence")
    public boolean hasOrderedReward(@PathVariable Long projectId) {
        return orderApiService.hasOrderedReward(projectId);
    }
}
