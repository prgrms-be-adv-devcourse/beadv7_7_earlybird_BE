package com.growmighty.lectures.firstday.user.presentation.dto;

import com.growmighty.lectures.firstday.user.application.dto.CreatorProfileInfo;

public record AdminCreatorResponse(
        Long userId,
        String name,
        String phoneNumber,
        String bankName,
        String bankCode,
        String accountHolder
) {
    public static AdminCreatorResponse from(CreatorProfileInfo info) {
        return new AdminCreatorResponse(info.userId(), info.name(), info.phoneNumber(),
                info.bankName(), info.bankCode(), info.accountHolder());
    }
}
