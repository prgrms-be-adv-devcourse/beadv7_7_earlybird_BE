package com.growmighty.lectures.firstday.order.infrastructure.client.dto;

import java.util.List;

public record CartApiData(Long userId, List<Item> items) {
    public record Item(Long rewardId, int quantity) {
    }
}
