package com.growmighty.lectures.firstday.order.presentation;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.order.application.OrderApiService;
import com.growmighty.lectures.firstday.order.presentation.dto.OrderConsistencyResponse;
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
    public ApiResponse<List<OrderResponse>> getMyOrders(@RequestParam Long userId) {
        List<OrderResponse> responses = orderApiService.getOrdersByUser(userId).stream()
                .map(OrderResponse::from)
                .toList();
        return ApiResponse.ok(responses);
    }

    @PostMapping
    public ApiResponse<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        return ApiResponse.ok(OrderResponse.from(orderApiService.placeOrder(request.toCommand())));
    }

    /** 후원 상세. TODO(팀): 본인(또는 해당 창작자)만 조회 가능 — 인증 도입 후 검증 */
    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable Long orderId) {
        return ApiResponse.ok(OrderResponse.from(orderApiService.getOrderInfo(orderId)));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderResponse> cancelOrder(@PathVariable Long orderId) {
        return ApiResponse.ok(OrderResponse.from(orderApiService.cancelOrder(orderId)));
    }

    @GetMapping("/{orderId}/inspect")
    public ApiResponse<OrderConsistencyResponse> inspectOrder(@PathVariable Long orderId) {
        return ApiResponse.ok(OrderConsistencyResponse.from(orderApiService.inspectOrder(orderId)));
    }
}
