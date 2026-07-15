package com.growmighty.lectures.firstday.cart.presentation;

import com.growmighty.lectures.firstday.cart.application.CartService;
import com.growmighty.lectures.firstday.cart.presentation.dto.AddCartItemRequest;
import com.growmighty.lectures.firstday.cart.presentation.dto.CartResponse;
import com.growmighty.lectures.firstday.cart.presentation.dto.ChangeCartItemQuantityRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/{userId}/cart")
public class CartController {
    private final CartService cartService;

    @GetMapping
    public CartResponse getCart(@PathVariable Long userId) {
        return CartResponse.from(cartService.getCart(userId));
    }

    @PostMapping("/items")
    public CartResponse addItem(@PathVariable Long userId, @RequestBody AddCartItemRequest request) {
        return CartResponse.from(cartService.addItem(request.toCommand(userId)));
    }

    @PatchMapping("/items/{rewardId}")
    public CartResponse changeQuantity(@PathVariable Long userId, @PathVariable Long rewardId, @RequestBody ChangeCartItemQuantityRequest request) {
        return CartResponse.from(cartService.changeQuantity(userId, rewardId, request.quantity()));
    }

    @DeleteMapping("/items/{rewardId}")
    public CartResponse removeItem(@PathVariable Long userId, @PathVariable Long rewardId) {
        return CartResponse.from(cartService.removeItem(userId, rewardId));
    }

    @DeleteMapping
    public Void clear(@PathVariable Long userId) {
        cartService.clear(userId);
        return null;
    }
}
