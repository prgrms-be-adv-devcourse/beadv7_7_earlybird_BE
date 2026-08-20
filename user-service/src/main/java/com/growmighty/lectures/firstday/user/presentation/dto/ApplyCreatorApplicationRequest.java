package com.growmighty.lectures.firstday.user.presentation.dto;

import com.growmighty.lectures.firstday.user.application.dto.ApplyCreatorApplicationCommand;
import com.growmighty.lectures.firstday.user.domain.BankCode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record ApplyCreatorApplicationRequest(
        @NotBlank String creatorName,
        @NotBlank String category,
        @NotBlank String introduction,
        String businessNumber,
        String portfolioUrl,
        @NotBlank String bankCode,
        @NotBlank String accountNumber,
        @NotBlank String accountHolder
) {
    public ApplyCreatorApplicationCommand toCommand(Long userId) {
        return new ApplyCreatorApplicationCommand(userId, creatorName, category, introduction,
                businessNumber, portfolioUrl, bankCode, accountNumber, accountHolder);
    }

    @AssertTrue(message = "잘못된 은행코드 입니다")
    private boolean isValidBankCode() {
        try {
            BankCode.fromCode(bankCode);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
