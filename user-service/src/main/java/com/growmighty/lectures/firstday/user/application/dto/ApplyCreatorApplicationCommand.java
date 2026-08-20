package com.growmighty.lectures.firstday.user.application.dto;

public record ApplyCreatorApplicationCommand(
        Long userId,
        String creatorName,
        String category,
        String introduction,
        String businessNumber,
        String portfolioUrl,
        String bankCode,
        String accountNumber,
        String accountHolder
) {
}
