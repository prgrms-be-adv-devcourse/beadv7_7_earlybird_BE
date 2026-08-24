package com.growmighty.lectures.firstday.ai.chat.presentation.dto;

import com.growmighty.lectures.firstday.ai.policy.infrastructure.search.PolicyChunkResult;
import com.growmighty.lectures.firstday.ai.tool.feign.port.project.dto.ProjectSearchResult;

import java.util.List;

public record ChatStreamMetadata(
    List<String> toolsUsed,
    List<ChatMessageResponse.PolicyReference> references,
    List<ProjectCard> projects
) {
    public static ChatStreamMetadata of(
        List<String> toolsUsed,
        List<PolicyChunkResult> policyReferences,
        List<ProjectSearchResult> projects) {
        return new ChatStreamMetadata(
            toolsUsed,
            policyReferences.stream().map(ChatMessageResponse.PolicyReference::from).distinct().toList(),
            projects.stream().map(ProjectCard::from).distinct().toList()
        );
    }

    public record ProjectCard(Long projectId, String title, String thumbnailUrl) {
        public static ProjectCard from(ProjectSearchResult result) {
            return new ProjectCard(result.projectId(), result.title(), result.thumbnailUrl());
        }
    }
}
