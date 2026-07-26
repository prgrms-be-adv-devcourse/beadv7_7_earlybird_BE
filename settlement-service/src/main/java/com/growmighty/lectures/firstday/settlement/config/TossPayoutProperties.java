package com.growmighty.lectures.firstday.settlement.config;

import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "settlement.toss-payout")
public final class TossPayoutProperties {

    private static final String TEST_SECRET_KEY_PREFIX = "test_sk";
    private static final Pattern SECURITY_KEY_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");

    private final boolean enabled;
    private final String secretKey;
    private final byte[] securityKey;

    public TossPayoutProperties(boolean enabled, String secretKey, String securityKey) {
        this.enabled = enabled;

        if (!enabled) {
            this.secretKey = null;
            this.securityKey = null;
            return;
        }
        if (secretKey == null || !secretKey.startsWith(TEST_SECRET_KEY_PREFIX)) {
            throw new IllegalArgumentException("토스 지급대행 MVP는 테스트 시크릿 키만 사용할 수 있습니다.");
        }
        if (securityKey == null || !SECURITY_KEY_PATTERN.matcher(securityKey).matches()) {
            throw new IllegalArgumentException("토스 지급대행 보안 키는 64자리 16진수 문자열이어야 합니다.");
        }

        this.secretKey = secretKey;
        this.securityKey = HexFormat.of().parseHex(securityKey);
    }

    public boolean enabled() {
        return enabled;
    }

    public String secretKey() {
        requireEnabled();
        return secretKey;
    }

    public byte[] securityKeyBytes() {
        requireEnabled();
        return securityKey.clone();
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("토스 지급대행 연동이 비활성화되어 있습니다.");
        }
    }
}
