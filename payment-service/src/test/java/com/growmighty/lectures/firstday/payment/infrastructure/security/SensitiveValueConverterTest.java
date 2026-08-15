package com.growmighty.lectures.firstday.payment.infrastructure.security;

import com.growmighty.lectures.firstday.payment.config.PaymentSecurityProperties;
import com.growmighty.lectures.firstday.payment.domain.vo.SensitiveValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveValueConverterTest {
    private static final String TEST_AES_256_KEY = "MDEyMzQ1Njc4OUFCQ0RFRjAxMjM0NTY3ODlBQkNERUY=";
    private final SensitiveValueConverter converter = new SensitiveValueConverter(
        new PaymentSensitiveDataCrypto(new PaymentSecurityProperties(TEST_AES_256_KEY))
    );

    // 추가 : 민감 값의 암복호화 왕복을 검증한다.
    @Test
    void convertDatabaseAndEntityAttribute_roundTripsSensitiveValue() {
        SensitiveValue value = new SensitiveValue("tgen_20260729_payment_key");

        String encrypted = converter.convertToDatabaseColumn(value);

        assertThat(encrypted).isNotEqualTo(value.value());
        assertThat(converter.convertToEntityAttribute(encrypted)).isEqualTo(value);
    }
}
