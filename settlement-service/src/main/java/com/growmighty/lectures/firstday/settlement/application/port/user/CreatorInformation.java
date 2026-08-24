package com.growmighty.lectures.firstday.settlement.application.port.user;

public record CreatorInformation(
        String email,
        String name,
        String phoneNumber
) {

    public CreatorInformation {
        email = required(email, "이메일");
        name = required(name, "이름");
        phoneNumber = required(phoneNumber, "전화번호");
    }

    @Override
    public String toString() {
        return "CreatorInformation[REDACTED]";
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은 필수입니다.");
        }
        return value;
    }
}
