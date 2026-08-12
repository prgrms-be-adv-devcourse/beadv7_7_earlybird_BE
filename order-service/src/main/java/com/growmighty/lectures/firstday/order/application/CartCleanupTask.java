package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.order.domain.CartCleanupType;

import java.util.List;

record CartCleanupTask(Long outboxId, Long orderId, Long userId, List<Long> rewardIds,
                       CartCleanupType cleanupType, int retryCount) {
}
