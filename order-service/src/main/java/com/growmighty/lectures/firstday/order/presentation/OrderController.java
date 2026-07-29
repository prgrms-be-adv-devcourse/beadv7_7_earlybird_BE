package com.growmighty.lectures.firstday.order.presentation;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.exception.BusinessException;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.order.application.OrderApiService;
import com.growmighty.lectures.firstday.order.presentation.dto.OrderResponse;
import com.growmighty.lectures.firstday.order.presentation.dto.PlaceOrderRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
    public List<OrderResponse> getMyOrders(@RequestParam Long userId, @RequestHeader(JwtHeaders.USER_ID) Long requesterId, @RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole) {
        validateBacker(requesterRole);
        validateRequester(userId, requesterId);
        return orderApiService.getOrdersByUser(requesterId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @PostMapping
    public OrderResponse placeOrder(@Valid @RequestBody PlaceOrderRequest request, @RequestHeader(JwtHeaders.USER_ID) Long requesterId, @RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole) {
        validateBacker(requesterRole);
        return OrderResponse.from(orderApiService.placeOrder(request.toCommand(requesterId), requesterId));
    }

    /** 후원 상세. */
    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable UUID orderId, @RequestParam(required = false) Long userId, @RequestHeader(JwtHeaders.USER_ID) Long requesterId, @RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole) {
        validateBacker(requesterRole);
        validateRequesterIfPresent(userId, requesterId);
        return OrderResponse.from(orderApiService.getOrderInfo(orderId, requesterId));
    }

    /** 후원 취소 */
    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancelOrder(@PathVariable UUID orderId, @RequestParam(required = false) Long userId, @RequestHeader(JwtHeaders.USER_ID) Long requesterId, @RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole) {
        validateBacker(requesterRole);
        validateRequesterIfPresent(userId, requesterId);
        return OrderResponse.from(orderApiService.cancelOrder(orderId, requesterId));
    }

    private void validateBacker(UserRole requesterRole) {
        if (requesterRole != UserRole.BACKER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Only backers can access order APIs.");
        }
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
