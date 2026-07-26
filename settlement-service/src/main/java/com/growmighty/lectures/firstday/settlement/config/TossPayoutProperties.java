package com.growmighty.lectures.firstday.settlement.config;

import java.net.URI;
import java.time.Duration;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "settlement.toss-payout")
public final class TossPayoutProperties {

    private static final String TEST_SECRET_KEY_PREFIX = "test_sk";
    private static final URI DEFAULT_BASE_URL = URI.create("https://api.tosspayments.com");
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(10);
    private static final Pattern SECURITY_KEY_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");

    private final boolean enabled;
    private final String secretKey;
    private final byte[] securityKey;
    private final URI baseUrl;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    public TossPayoutProperties(
            boolean enabled,
            String secretKey,
            String securityKey,
            String baseUrl,
            Duration connectTimeout,
            Duration readTimeout
    ) {
        this.enabled = enabled;
        this.baseUrl = validateBaseUrl(baseUrl);
        this.connectTimeout = validateTimeout(
                connectTimeout,
                DEFAULT_CONNECT_TIMEOUT,
                "연결 제한시간"
        );
        this.readTimeout = validateTimeout(
                readTimeout,
                DEFAULT_READ_TIMEOUT,
                "응답 제한시간"
        );

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

    public URI baseUrl() {
        requireEnabled();
        return baseUrl;
    }

    public Duration connectTimeout() {
        requireEnabled();
        return connectTimeout;
    }

    public Duration readTimeout() {
        requireEnabled();
        return readTimeout;
    }

    private static URI validateBaseUrl(String baseUrl) {
        URI uri = baseUrl == null || baseUrl.isBlank()
                ? DEFAULT_BASE_URL
                : URI.create(baseUrl);
        if (!uri.isAbsolute()
                || !("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("토스 지급대행 기본 주소는 HTTP 또는 HTTPS 절대 URI여야 합니다.");
        }
        return uri;
    }

    private static Duration validateTimeout(
            Duration timeout,
            Duration defaultTimeout,
            String propertyName
    ) {
        Duration resolved = timeout == null ? defaultTimeout : timeout;
        if (resolved.isZero() || resolved.isNegative()) {
            throw new IllegalArgumentException("토스 지급대행 " + propertyName + "은 양수여야 합니다.");
        }
        return resolved;
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("토스 지급대행 연동이 비활성화되어 있습니다.");
        }
    }
}
