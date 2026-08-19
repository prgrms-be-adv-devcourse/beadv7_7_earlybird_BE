package com.growmighty.lectures.firstday.ai.policy.infrastructure.search;

import com.growmighty.lectures.firstday.ai.policy.domain.PolicyCategory;

public record PolicyChunkResult(
    PolicyCategory category,
    String topic,
    String content
) {
}
