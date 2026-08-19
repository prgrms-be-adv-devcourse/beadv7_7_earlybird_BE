package com.growmighty.lectures.firstday.cart.presentation;

import com.growmighty.lectures.firstday.cart.application.CartService;
import com.growmighty.lectures.firstday.cart.presentation.dto.AddCartItemsRequest;
import com.growmighty.lectures.firstday.cart.presentation.dto.CartResponse;
import com.growmighty.lectures.firstday.cart.presentation.dto.UpdateCartItemsRequest;
import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.exception.BusinessException;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/carts")
public class CartController {
    private final CartService cartService;

    // TODO(예정): 권한 관련 설정, cart -> order 성공 시 해당 reward들 비우는 부분 추가

    @GetMapping
    public CartResponse getCart(@RequestHeader(JwtHeaders.USER_ID) Long requesterId) {
        return CartResponse.from(cartService.getCart(requesterId));
    }

    @PostMapping("/items")
    public CartResponse addItems(@RequestHeader(JwtHeaders.USER_ID) Long requesterId, @Valid @RequestBody AddCartItemsRequest request) {
        return CartResponse.from(cartService.addItems(request.toCommand(requesterId)));
    }

    @PatchMapping("/items")
    public CartResponse updateItems(@RequestHeader(JwtHeaders.USER_ID) Long requesterId, @Valid @RequestBody UpdateCartItemsRequest request) {
        return CartResponse.from(cartService.updateItems(request.toCommand(requesterId)));
    }

    @DeleteMapping("/items/{rewardId}")
    public CartResponse removeItem(@PathVariable Long rewardId, @RequestHeader(JwtHeaders.USER_ID) Long requesterId) {
        return CartResponse.from(cartService.removeItem(requesterId, rewardId));
    }

    @DeleteMapping
    public Void clear(@RequestHeader(JwtHeaders.USER_ID) Long requesterId) {
        cartService.clear(requesterId);
        return null;
    }
}
