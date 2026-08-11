package com.growmighty.lectures.firstday.project.project.presentation.dto.response;

import com.growmighty.lectures.firstday.project.project.application.port.ProjectSuggestion;

public record ProjectAutocompleteResponse(Long projectId, String title) {
    public static ProjectAutocompleteResponse from(ProjectSuggestion suggestion) {
        return new ProjectAutocompleteResponse(suggestion.projectId(), suggestion.title());
    }
}
