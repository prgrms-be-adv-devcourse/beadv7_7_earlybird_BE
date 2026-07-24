package com.growmighty.lectures.firstday.cart.presentation;

import com.growmighty.lectures.firstday.cart.application.CartService;
import com.growmighty.lectures.firstday.cart.presentation.dto.AddCartItemsRequest;
import com.growmighty.lectures.firstday.cart.presentation.dto.CartResponse;
import com.growmighty.lectures.firstday.cart.presentation.dto.UpdateCartItemsRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public CartResponse addItems(@PathVariable Long userId, @RequestBody AddCartItemsRequest request) {
        return CartResponse.from(cartService.addItems(request.toCommand(userId)));
    }

    @PatchMapping("/items")
    public CartResponse updateItemQuantities(@PathVariable Long userId, @RequestBody UpdateCartItemsRequest request) {
        return CartResponse.from(cartService.updateItemQuantities(request.toCommand(userId)));
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
