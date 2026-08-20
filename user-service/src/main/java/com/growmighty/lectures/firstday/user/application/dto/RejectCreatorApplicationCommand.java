package com.growmighty.lectures.firstday.user.application.dto;

public record RejectCreatorApplicationCommand(
        Long applicationId,
        String reason
) {
}
