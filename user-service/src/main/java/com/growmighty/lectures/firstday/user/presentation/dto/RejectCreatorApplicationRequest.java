package com.growmighty.lectures.firstday.user.presentation.dto;

import com.growmighty.lectures.firstday.user.application.dto.RejectCreatorApplicationCommand;
import jakarta.validation.constraints.NotBlank;

public record RejectCreatorApplicationRequest(
        @NotBlank String reason
) {
    public RejectCreatorApplicationCommand toCommand(Long applicationId) {
        return new RejectCreatorApplicationCommand(applicationId, reason);
    }
}
