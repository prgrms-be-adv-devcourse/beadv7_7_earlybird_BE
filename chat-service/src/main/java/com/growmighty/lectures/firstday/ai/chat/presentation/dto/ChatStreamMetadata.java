package com.growmighty.lectures.firstday.ai.chat.presentation.dto;

import com.growmighty.lectures.firstday.ai.policy.infrastructure.search.PolicyChunkResult;

import java.util.List;

public record ChatStreamMetadata(
    List<String> toolsUsed,
    List<ChatMessageResponse.PolicyReference> references
) {
    public static ChatStreamMetadata of(List<String> toolsUsed, List<PolicyChunkResult> policyReferences) {
        return new ChatStreamMetadata(
            toolsUsed,
            policyReferences.stream().map(ChatMessageResponse.PolicyReference::from).distinct().toList()
        );
    }
}
