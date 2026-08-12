package com.growmighty.lectures.firstday.cart.presentation;

import com.growmighty.lectures.firstday.cart.application.CartService;
import com.growmighty.lectures.firstday.cart.application.dto.CartSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/carts/users/{userId}")
public class InternalCartController {
    private final CartService cartService;

    @GetMapping
    public CartSnapshot getCart(@PathVariable Long userId) {
        return cartService.getSnapshot(userId);
    }

    @DeleteMapping("/items")
    public void removeItems(@PathVariable Long userId, @RequestBody RemoveCartItemsRequest request) {
        cartService.removeItems(userId, request.rewardIds());
    }

    public record RemoveCartItemsRequest(List<Long> rewardIds) {
    }
}
