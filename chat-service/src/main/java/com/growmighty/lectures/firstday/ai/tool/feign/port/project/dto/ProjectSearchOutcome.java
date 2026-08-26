package com.growmighty.lectures.firstday.ai.tool.feign.port.project.dto;

import java.util.List;

public record ProjectSearchOutcome(
    List<ProjectSearchResult> projects,
    boolean hasMore,
    int totalCount
) {
}
