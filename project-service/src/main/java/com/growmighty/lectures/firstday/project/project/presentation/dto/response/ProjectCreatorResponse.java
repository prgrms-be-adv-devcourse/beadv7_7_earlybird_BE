package com.growmighty.lectures.firstday.project.project.presentation.dto.response;

import com.growmighty.lectures.firstday.project.project.domain.Project;

public record ProjectCreatorResponse(Long creatorId) {
    public static ProjectCreatorResponse from(Project project) {
        return new ProjectCreatorResponse(project.getCreatorId());
    }
}
