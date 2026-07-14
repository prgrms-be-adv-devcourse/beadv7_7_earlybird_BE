package com.growmighty.lectures.firstday.cart.presentation;

import com.growmighty.lectures.firstday.cart.application.CartService;
import com.growmighty.lectures.firstday.cart.presentation.dto.AddCartItemRequest;
import com.growmighty.lectures.firstday.cart.presentation.dto.CartResponse;
import com.growmighty.lectures.firstday.cart.presentation.dto.ChangeCartItemQuantityRequest;
import com.growmighty.lectures.firstday.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/{userId}/cart")
public class CartController {
    private final CartService cartService;

    @GetMapping
    public ApiResponse<CartResponse> getCart(@PathVariable Long userId) {
        return ApiResponse.ok(CartResponse.from(cartService.getCart(userId)));
    }

    @PostMapping("/items")
    public ApiResponse<CartResponse> addItem(@PathVariable Long userId, @RequestBody AddCartItemRequest request) {
        return ApiResponse.ok(CartResponse.from(cartService.addItem(request.toCommand(userId))));
    }

    @PatchMapping("/items/{projectId}")
    public ApiResponse<CartResponse> changeQuantity(@PathVariable Long userId, @PathVariable Long projectId, @RequestBody ChangeCartItemQuantityRequest request) {
        return ApiResponse.ok(CartResponse.from(cartService.changeQuantity(userId, projectId, request.quantity())));
    }

    @DeleteMapping("/items/{projectId}")
    public ApiResponse<CartResponse> removeItem(@PathVariable Long userId, @PathVariable Long projectId) {
        return ApiResponse.ok(CartResponse.from(cartService.removeItem(userId, projectId)));
    }

    @DeleteMapping
    public ApiResponse<Void> clear(@PathVariable Long userId) {
        cartService.clear(userId);
        return ApiResponse.ok();
    }
}
