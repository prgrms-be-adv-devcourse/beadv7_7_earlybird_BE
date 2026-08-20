package com.growmighty.lectures.firstday.user.infrastructure.security;

import com.growmighty.lectures.firstday.user.config.UserSecurityProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountNumberConverterTest {
    private static final String TEST_AES_256_KEY = "MDEyMzQ1Njc4OUFCQ0RFRjAxMjM0NTY3ODlBQkNERUY=";
    private final AccountNumberConverter converter = new AccountNumberConverter(
        new UserSensitiveDataCrypto(new UserSecurityProperties(TEST_AES_256_KEY))
    );

    @Test
    void convertDatabaseAndEntityAttribute_roundTripsAccountNumber() {
        String accountNumber = "110-123-456789";

        String encrypted = converter.convertToDatabaseColumn(accountNumber);

        assertThat(encrypted).isNotEqualTo(accountNumber);
        assertThat(converter.convertToEntityAttribute(encrypted)).isEqualTo(accountNumber);
    }
}
