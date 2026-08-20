package com.growmighty.lectures.firstday.user.presentation.dto;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.user.application.dto.ChangeRoleCommand;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(
        @NotNull UserRole role
) {
    public ChangeRoleCommand toCommand(Long userId) {
        return new ChangeRoleCommand(userId, role);
    }
}
