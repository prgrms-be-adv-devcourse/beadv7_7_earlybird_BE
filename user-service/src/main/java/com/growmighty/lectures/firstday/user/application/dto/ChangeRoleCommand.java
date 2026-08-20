package com.growmighty.lectures.firstday.user.application.dto;

import com.growmighty.lectures.firstday.common.entity.UserRole;

public record ChangeRoleCommand(
        Long userId,
        UserRole role
) {
}
