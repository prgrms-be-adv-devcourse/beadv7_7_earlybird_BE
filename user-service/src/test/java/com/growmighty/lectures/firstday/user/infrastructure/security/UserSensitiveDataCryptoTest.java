package com.growmighty.lectures.firstday.user.infrastructure.security;

import com.growmighty.lectures.firstday.user.config.UserSecurityProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserSensitiveDataCryptoTest {
    private static final String TEST_AES_256_KEY = "MDEyMzQ1Njc4OUFCQ0RFRjAxMjM0NTY3ODlBQkNERUY=";
    private final UserSensitiveDataCrypto crypto = new UserSensitiveDataCrypto(
        new UserSecurityProperties(TEST_AES_256_KEY)
    );

    @Test
    void encrypt_thenDecrypt_returnsOriginalPlainText() {
        String accountNumber = "110-123-456789";

        String encrypted = crypto.encrypt(accountNumber);

        assertThat(encrypted).isNotEqualTo(accountNumber);
        assertThat(crypto.decrypt(encrypted)).isEqualTo(accountNumber);
    }

    // 매 암호화마다 새 IV를 사용해 같은 계좌번호의 암호문이 달라지는지 검증
    @Test
    void encrypt_samePlainText_returnsDifferentCipherTexts() {
        String accountNumber = "110-123-456789";

        String firstEncrypted = crypto.encrypt(accountNumber);
        String secondEncrypted = crypto.encrypt(accountNumber);

        assertThat(firstEncrypted).isNotEqualTo(secondEncrypted);
        assertThat(crypto.decrypt(firstEncrypted)).isEqualTo(accountNumber);
        assertThat(crypto.decrypt(secondEncrypted)).isEqualTo(accountNumber);
    }

    @Test
    void decrypt_tamperedCipherText_throws() {
        String encrypted = crypto.encrypt("110-123-456789");
        String tampered = encrypted.substring(0, encrypted.length() - 1) + "A";

        assertThatThrownBy(() -> crypto.decrypt(tampered))
            .isInstanceOf(IllegalStateException.class);
    }
}
