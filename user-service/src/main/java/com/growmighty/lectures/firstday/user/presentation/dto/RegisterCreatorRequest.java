package com.growmighty.lectures.firstday.user.presentation.dto;

import com.growmighty.lectures.firstday.user.application.dto.RegisterCreatorCommand;
import com.growmighty.lectures.firstday.user.domain.BankCode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record RegisterCreatorRequest(
		@NotBlank String bankCode,
		@NotBlank String accountNumber,
		@NotBlank String accountHolder
) {
	public RegisterCreatorCommand toCommand(Long userId) {
		return new RegisterCreatorCommand(userId, bankCode, accountNumber, accountHolder);
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
