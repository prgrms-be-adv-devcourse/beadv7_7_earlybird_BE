package com.growmighty.lectures.firstday.order.presentation;

import com.growmighty.lectures.firstday.order.application.OrderApiService;
import com.growmighty.lectures.firstday.order.presentation.dto.OrderResponse;
import com.growmighty.lectures.firstday.order.presentation.dto.PlaceOrderRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

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
    public OrderResponse getOrder(@PathVariable UUID orderId, @RequestParam(required = false) Long userId) {
        if (userId == null) {
            return OrderResponse.from(orderApiService.getOrderInfo(orderId));
        }
        return OrderResponse.from(orderApiService.getOrderInfo(orderId, userId));
    }

    /** 후원 취소 */
    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancelOrder(@PathVariable UUID orderId, @RequestParam(required = false) Long userId) {
        return OrderResponse.from(orderApiService.cancelOrder(orderId, userId));
    }
}
