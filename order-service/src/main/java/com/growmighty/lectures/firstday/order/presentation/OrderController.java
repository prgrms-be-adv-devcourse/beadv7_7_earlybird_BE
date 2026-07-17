package com.growmighty.lectures.firstday.order.presentation;

import com.growmighty.lectures.firstday.order.application.OrderApiService;
import com.growmighty.lectures.firstday.order.presentation.dto.OrderConsistencyResponse;
import com.growmighty.lectures.firstday.order.presentation.dto.OrderInspectionRequest;
import com.growmighty.lectures.firstday.order.presentation.dto.OrderInspectionResponse;
import com.growmighty.lectures.firstday.order.presentation.dto.OrderResponse;
import com.growmighty.lectures.firstday.order.presentation.dto.PlaceOrderRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/orders")
public class OrderController {

    private final OrderApiService orderApiService;

    /** 내 후원 내역. TODO(팀): JWT 도입 후 userId 파라미터 대신 토큰에서 추출 */
    @GetMapping("/me")
    public List<OrderResponse> getMyOrders(@RequestParam Long userId) {
        return orderApiService.getOrdersByUser(userId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @PostMapping
    public OrderResponse placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        return OrderResponse.from(orderApiService.placeOrder(request.toCommand()));
    }

    /** 후원 상세. TODO(팀): 본인(또는 해당 창작자)만 조회 가능 — 인증 도입 후 검증 */
    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable Long orderId) {
        return OrderResponse.from(orderApiService.getOrderInfo(orderId));
    }

    /** 후원 취소 */
    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancelOrder(@PathVariable Long orderId) {
        return OrderResponse.from(orderApiService.cancelOrder(orderId));
    }

    /** 후원 시 결제 검증용 정보 저장  . TODO(팀): payment와 연동 — 인증 도입 후 검증 */
    @GetMapping("/{orderId}/inspect")
    public OrderConsistencyResponse inspectOrder(@PathVariable Long orderId) {
        return OrderConsistencyResponse.from(orderApiService.inspectOrder(orderId));
    }

    /** 후원 시 결제 정보 검증  . TODO(팀): payment와 연동 — 인증 도입 후 검증 */
    @PostMapping("/inspect")
    public OrderInspectionResponse placeOrderInspection(@Valid @RequestBody OrderInspectionRequest request) {
        return OrderInspectionResponse.from(orderApiService.placeOrderInspection(request.orderId()));
    }
}
