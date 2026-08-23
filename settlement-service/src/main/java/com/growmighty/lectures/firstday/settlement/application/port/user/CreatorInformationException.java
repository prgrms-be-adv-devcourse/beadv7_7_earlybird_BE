package com.growmighty.lectures.firstday.settlement.application.port.user;

import java.util.Objects;

public final class CreatorInformationException extends RuntimeException {

    public enum FailureType {
        AVAILABILITY,
        NOT_FOUND,
        CONTRACT
    }

    private final FailureType failureType;

    public CreatorInformationException(FailureType failureType, String message, Throwable cause) {
        super(message, cause);
        this.failureType = Objects.requireNonNull(failureType, "창작자 정보 조회 실패 유형은 필수입니다.");
    }

    public FailureType failureType() {
        return failureType;
    }
}
