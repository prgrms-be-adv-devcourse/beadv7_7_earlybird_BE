package com.growmighty.lectures.firstday.order.infrastructure.client;

import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.order.application.port.CartPort;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.CartApiData;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.RemoveCartItemsBody;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "cart-service")
public interface CartFeignClient extends CartPort {
    @GetMapping("/internal/v1/carts/users/{userId}")
    ApiResponse<CartApiData> fetchCart(@PathVariable("userId") Long userId);

    @DeleteMapping("/internal/v1/carts/users/{userId}/items")
    void sendRemoveItems(@PathVariable("userId") Long userId, @RequestBody RemoveCartItemsBody body);

    @Override
    default CartSnapshot getCart(Long userId) {
        ApiResponse<CartApiData> response = fetchCart(userId);
        if (response == null || !response.success() || response.data() == null || response.data().items() == null) {
            throw new ServiceUnavailableException("Cart response is unavailable. userId=" + userId);
        }
        CartApiData data = response.data();
        return new CartSnapshot(data.userId(), data.items().stream()
                .map(item -> new CartSnapshot.Item(item.rewardId(), item.quantity()))
                .toList());
    }

    @Override
    default void removeItems(Long userId, List<Long> rewardIds) {
        sendRemoveItems(userId, new RemoveCartItemsBody(rewardIds));
    }
}
