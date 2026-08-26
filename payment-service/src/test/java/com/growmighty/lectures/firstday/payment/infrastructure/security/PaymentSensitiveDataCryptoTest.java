package com.growmighty.lectures.firstday.payment.infrastructure.security;

import com.growmighty.lectures.firstday.payment.config.PaymentSecurityProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentSensitiveDataCryptoTest {
    private static final String TEST_AES_256_KEY = "MDEyMzQ1Njc4OUFCQ0RFRjAxMjM0NTY3ODlBQkNERUY=";
    private final PaymentSensitiveDataCrypto crypto = new PaymentSensitiveDataCrypto(
        new PaymentSecurityProperties(TEST_AES_256_KEY)
    );

    // 추가 : AES-GCM 암호문을 원래 결제 키로 복호화할 수 있는지 검증
    @Test
    void encrypt_thenDecrypt_returnsOriginalPlainText() {
        String paymentKey = "tgen_20260729_payment_key";

        String encrypted = crypto.encrypt(paymentKey);

        assertThat(encrypted).isNotEqualTo(paymentKey);
        assertThat(crypto.decrypt(encrypted)).isEqualTo(paymentKey);
    }

    // 추가 : 매 암호화마다 새 IV를 사용해 같은 결제 키의 암호문이 달라지는지 검증
    @Test
    void encrypt_samePlainText_returnsDifferentCipherTexts() {
        String paymentKey = "tgen_20260729_payment_key";

        String firstEncrypted = crypto.encrypt(paymentKey);
        String secondEncrypted = crypto.encrypt(paymentKey);

        assertThat(firstEncrypted).isNotEqualTo(secondEncrypted);
        assertThat(crypto.decrypt(firstEncrypted)).isEqualTo(paymentKey);
        assertThat(crypto.decrypt(secondEncrypted)).isEqualTo(paymentKey);
    }

    // 추가 : 암호문 또는 인증 태그가 변조되면 복호화하지 않는지 검증
    @Test
    void decrypt_tamperedCipherText_throws() {
        String encrypted = crypto.encrypt("tgen_20260729_payment_key");
        String tampered = encrypted.substring(0, encrypted.length() - 1) + "A";

        assertThatThrownBy(() -> crypto.decrypt(tampered))
            .isInstanceOf(IllegalStateException.class);
    }

    // 추가 : 잘못된 암호화 키 설정은 개발 키 폴백 없이 초기화를 실패시키는지 검증
    @Test
    void constructor_throwsWhenEncryptionKeyIsInvalid() {
        assertThatThrownBy(() -> new PaymentSensitiveDataCrypto(
            new PaymentSecurityProperties(" ")
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("AES-256");

        assertThatThrownBy(() -> new PaymentSensitiveDataCrypto(
            new PaymentSecurityProperties("not-base64")
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Base64");

        String aes128Key = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new PaymentSensitiveDataCrypto(
            new PaymentSecurityProperties(aes128Key)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("32바이트");
    }
}
