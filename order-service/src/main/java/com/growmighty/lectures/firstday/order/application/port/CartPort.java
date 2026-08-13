package com.growmighty.lectures.firstday.order.application.port;

import java.util.List;

public interface CartPort {
    CartSnapshot getCart(Long userId);

    void removeItems(Long userId, List<Long> rewardIds);

    record CartSnapshot(Long userId, List<Item> items) {
        public record Item(Long rewardId, int quantity) {
        }
    }
}
