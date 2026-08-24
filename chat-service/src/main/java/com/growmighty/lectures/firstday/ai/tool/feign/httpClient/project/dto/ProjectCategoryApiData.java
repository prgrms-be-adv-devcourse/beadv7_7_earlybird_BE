package com.growmighty.lectures.firstday.ai.tool.feign.httpClient.project.dto;

import java.util.List;

public record ProjectCategoryApiData(
    Long id,
    Long parentProjectCategoryId,
    String name,
    List<ProjectCategoryApiData> children
) {
}
