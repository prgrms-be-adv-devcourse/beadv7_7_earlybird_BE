package com.growmighty.lectures.firstday.ai.chat.presentation.dto;

import com.growmighty.lectures.firstday.ai.policy.domain.PolicyCategory;
import com.growmighty.lectures.firstday.ai.policy.infrastructure.search.PolicyChunkResult;

import java.util.List;

public record ChatMessageResponse(
    String reply,
    List<String> toolsUsed,
    List<PolicyReference> references
) {
    public static ChatMessageResponse of(String reply, List<String> toolsUsed, List<PolicyChunkResult> policyReferences) {
        return new ChatMessageResponse(
            reply,
            toolsUsed,
            policyReferences.stream().map(PolicyReference::from).toList()
        );
    }

    public record PolicyReference(PolicyCategory category, String topic) {
        public static PolicyReference from(PolicyChunkResult chunk) {
            return new PolicyReference(chunk.category(), chunk.topic());
        }
    }
}
