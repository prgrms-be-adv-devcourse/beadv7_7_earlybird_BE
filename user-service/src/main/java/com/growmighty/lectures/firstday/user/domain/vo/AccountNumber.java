package com.growmighty.lectures.firstday.user.domain.vo;

public record AccountNumber(String value) {
	public AccountNumber {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("정산 계좌 정보는 비어 있을 수 없습니다.");
		}
	}

	@Override
	public String toString() {
		return "AccountNumber [value=REDACTED]";
	}
}
