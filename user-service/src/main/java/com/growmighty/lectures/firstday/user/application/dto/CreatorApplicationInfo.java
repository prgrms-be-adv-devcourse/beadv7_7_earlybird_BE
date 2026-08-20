package com.growmighty.lectures.firstday.user.application.dto;

import com.growmighty.lectures.firstday.user.domain.CreatorApplication;
import com.growmighty.lectures.firstday.user.domain.CreatorApplicationStatus;

public record CreatorApplicationInfo(
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
    public static CreatorApplicationInfo from(CreatorApplication application) {
        return new CreatorApplicationInfo(
                application.getId(),
                application.getUserId(),
                application.getCreatorName(),
                application.getCategory(),
                application.getIntroduction(),
                application.getBusinessNumber(),
                application.getPortfolioUrl(),
                application.getBankName(),
                application.getBankCode(),
                application.getAccountNumber(),
                application.getAccountHolder(),
                application.getStatus(),
                application.getRejectReason());
    }
}
