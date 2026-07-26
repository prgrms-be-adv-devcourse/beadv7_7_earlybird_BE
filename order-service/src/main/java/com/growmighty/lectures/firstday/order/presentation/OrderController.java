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

    // TODO(예정): 권한 관련 설정

    /** 내 후원 내역. */
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

    /** 후원 상세. */
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
