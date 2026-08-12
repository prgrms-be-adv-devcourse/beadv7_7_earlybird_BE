package com.growmighty.lectures.firstday.order.application;

import java.util.List;

record CartCleanupTask(Long outboxId, Long orderId, Long userId, List<Long> rewardIds, int retryCount) {
}
