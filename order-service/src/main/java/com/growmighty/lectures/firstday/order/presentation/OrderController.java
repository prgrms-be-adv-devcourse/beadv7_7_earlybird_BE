package com.growmighty.lectures.firstday.order.presentation;

import com.growmighty.lectures.firstday.common.exception.BusinessException;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.order.application.OrderApiService;
import com.growmighty.lectures.firstday.order.presentation.dto.OrderResponse;
import com.growmighty.lectures.firstday.order.presentation.dto.OrderSummaryResponse;
import com.growmighty.lectures.firstday.order.presentation.dto.PlaceOrderRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderApiService orderApiService;

    // TODO(예정): 권한 관련 설정

    /** 내 후원 내역. */
    @GetMapping("/me")
    public List<OrderSummaryResponse> getMyOrders(@RequestParam Long userId,
                                                  @RequestHeader(JwtHeaders.USER_ID) Long requesterId) {
        validateRequester(userId, requesterId);
        return orderApiService.getOrdersByUser(requesterId).stream()
                .map(OrderSummaryResponse::from)
                .toList();
    }

    @PostMapping
    public OrderResponse placeOrder(@Valid @RequestBody PlaceOrderRequest request,
                                    @RequestHeader(JwtHeaders.USER_ID) Long requesterId) {
        return OrderResponse.created(orderApiService.placeOrder(request.toCommand(requesterId), requesterId));
    }

    /** 후원 상세. */
    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable Long orderId,
                                  @RequestParam(required = false) Long userId,
                                  @RequestHeader(JwtHeaders.USER_ID) Long requesterId) {
        validateRequesterIfPresent(userId, requesterId);
        return OrderResponse.detail(orderApiService.getOrderInfo(orderId, requesterId));
    }

    /** 후원 취소 */
    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancelOrder(@PathVariable Long orderId,
                                     @RequestParam(required = false) Long userId,
                                     @RequestHeader(JwtHeaders.USER_ID) Long requesterId) {
        validateRequesterIfPresent(userId, requesterId);
        return OrderResponse.detail(orderApiService.cancelOrder(orderId, requesterId));
    }

    private void validateRequester(Long userId, Long requesterId) {
        if (!requesterId.equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Order access denied. userId=" + userId);
        }
    }

    private void validateRequesterIfPresent(Long userId, Long requesterId) {
        if (userId != null) {
            validateRequester(userId, requesterId);
        }
    }
}
