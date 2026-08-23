package com.growmighty.lectures.firstday.settlement.infrastructure.client.user;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "settlement.creator-information.http")
public final class UserCreatorInformationClientProperties {

    private static final URI DEFAULT_BASE_URL = URI.create("http://user-service");
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(3);

    private final URI baseUrl;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    public UserCreatorInformationClientProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        this.baseUrl = baseUrl(baseUrl);
        this.connectTimeout = timeout(connectTimeout, DEFAULT_CONNECT_TIMEOUT, "연결 제한시간");
        this.readTimeout = timeout(readTimeout, DEFAULT_READ_TIMEOUT, "응답 제한시간");
    }

    public URI baseUrl() { return baseUrl; }
    public Duration connectTimeout() { return connectTimeout; }
    public Duration readTimeout() { return readTimeout; }

    private static URI baseUrl(String value) {
        URI uri = value == null || value.isBlank() ? DEFAULT_BASE_URL : URI.create(value);
        if (!uri.isAbsolute() || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("User 서비스 기본 주소는 HTTP 또는 HTTPS 절대 URI여야 합니다.");
        }
        return uri;
    }

    private static Duration timeout(Duration value, Duration defaultValue, String fieldName) {
        Duration resolved = value == null ? defaultValue : value;
        if (resolved.isZero() || resolved.isNegative()) {
            throw new IllegalArgumentException("User 서비스 " + fieldName + "은 양수여야 합니다.");
        }
        return resolved;
    }
}
