package com.growmighty.lectures.firstday.settlement.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TossPayoutPropertiesTest {

    private static final String SECURITY_KEY = "01".repeat(32);

    @Test
    @DisplayName("활성화된 지급대행 설정은 테스트 키와 32바이트 보안 키를 제공한다")
    void providesValidatedTestCredentials() {
        TossPayoutProperties properties = new TossPayoutProperties(
                true,
                "test_sk_example",
                SECURITY_KEY,
                null,
                null,
                null
        );

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.secretKey()).isEqualTo("test_sk_example");
        assertThat(properties.securityKeyBytes()).hasSize(32);
        assertThat(properties.baseUrl().toString()).isEqualTo("https://api.tosspayments.com");
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("비활성화 상태에서는 자격 증명이 없어도 된다")
    void allowsMissingCredentialsWhenDisabled() {
        TossPayoutProperties properties = new TossPayoutProperties(
                false,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(properties.enabled()).isFalse();
        assertThatThrownBy(properties::secretKey)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("라이브 시크릿 키로 테스트 지급대행을 활성화할 수 없다")
    void rejectsLiveSecretKey() {
        assertThatThrownBy(() -> new TossPayoutProperties(
                true,
                "live_sk_example",
                SECURITY_KEY,
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("테스트 시크릿 키");
    }

    @Test
    @DisplayName("보안 키는 64자리 16진수여야 한다")
    void rejectsInvalidSecurityKey() {
        assertThatThrownBy(() -> new TossPayoutProperties(
                true,
                "test_sk_example",
                "not-a-64-character-hex-key",
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64자리 16진수");
    }

    @Test
    @DisplayName("보안 키 바이트 배열은 외부에서 변경할 수 없다")
    void protectsSecurityKeyBytes() {
        TossPayoutProperties properties = new TossPayoutProperties(
                true,
                "test_sk_example",
                SECURITY_KEY,
                null,
                null,
                null
        );
        byte[] firstRead = properties.securityKeyBytes();

        firstRead[0] = 0x7f;

        assertThat(properties.securityKeyBytes()[0]).isEqualTo((byte) 0x01);
    }

    @Test
    @DisplayName("HTTP 대역 검증을 위해 토스 기본 주소를 외부 설정으로 교체할 수 있다")
    void supportsCustomBaseUrl() {
        TossPayoutProperties properties = new TossPayoutProperties(
                true,
                "test_sk_example",
                SECURITY_KEY,
                "http://localhost:18086",
                null,
                null
        );

        assertThat(properties.baseUrl().toString()).isEqualTo("http://localhost:18086");
    }
}
