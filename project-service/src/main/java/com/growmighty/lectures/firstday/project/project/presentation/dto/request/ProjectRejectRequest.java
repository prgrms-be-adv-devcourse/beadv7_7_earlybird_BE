package com.growmighty.lectures.firstday.project.project.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ProjectRejectRequest(
        @NotBlank String reason
) {
}
