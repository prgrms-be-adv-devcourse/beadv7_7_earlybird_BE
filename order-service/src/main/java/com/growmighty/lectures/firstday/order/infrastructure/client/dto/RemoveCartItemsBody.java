package com.growmighty.lectures.firstday.order.infrastructure.client.dto;

import java.util.List;

public record RemoveCartItemsBody(List<Long> rewardIds) {
}
