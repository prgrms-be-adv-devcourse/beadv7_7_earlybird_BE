package com.growmighty.lectures.firstday.user.presentation.dto;

import com.growmighty.lectures.firstday.user.application.dto.CreatorApplicationInfo;
import com.growmighty.lectures.firstday.user.domain.CreatorApplicationStatus;

public record CreatorApplicationResponse(
        Long id,
        Long userId,
        String creatorName,
        String category,
        String introduction,
        String businessNumber,
        String portfolioUrl,
        String bankName,
        String bankCode,
        String accountNumber,
        String accountHolder,
        CreatorApplicationStatus status,
        String rejectReason
) {
    public static CreatorApplicationResponse from(CreatorApplicationInfo info) {
        return new CreatorApplicationResponse(
                info.id(), info.userId(), info.creatorName(), info.category(), info.introduction(),
                info.businessNumber(), info.portfolioUrl(), info.bankName(), info.bankCode(), info.accountNumber(),
                info.accountHolder(), info.status(), info.rejectReason());
    }
}
