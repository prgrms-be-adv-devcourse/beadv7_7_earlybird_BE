package com.growmighty.lectures.firstday.ai.policy.infrastructure.loader;

import com.growmighty.lectures.firstday.ai.policy.domain.PolicyCategory;

public record PolicyChunk(
    String chunkId,
    PolicyCategory category,
    String topic,
    String content
) {
}
