package com.growmighty.lectures.firstday.payment.infrastructure.security;

import com.growmighty.lectures.firstday.payment.config.PaymentSecurityProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentSensitiveDataConverterTest {
    private static final String TEST_AES_256_KEY = "MDEyMzQ1Njc4OUFCQ0RFRjAxMjM0NTY3ODlBQkNERUY=";
    private final PaymentSensitiveDataConverter converter = new PaymentSensitiveDataConverter(
        new PaymentSensitiveDataCrypto(new PaymentSecurityProperties(TEST_AES_256_KEY))
    );

    // 추가 : JPA 저장·조회 변환이 암호화와 복호화를 각각 수행하는지 검증
    @Test
    void convertDatabaseAndEntityAttribute_roundTripsPlainText() {
        String paymentKey = "tgen_20260729_payment_key";

        String encrypted = converter.convertToDatabaseColumn(paymentKey);

        assertThat(encrypted).isNotEqualTo(paymentKey);
        assertThat(converter.convertToEntityAttribute(encrypted)).isEqualTo(paymentKey);
    }
}
