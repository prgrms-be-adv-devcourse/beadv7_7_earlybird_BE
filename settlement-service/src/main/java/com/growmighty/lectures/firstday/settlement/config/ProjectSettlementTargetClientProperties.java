package com.growmighty.lectures.firstday.settlement.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "settlement.project-target.http")
public final class ProjectSettlementTargetClientProperties {

    private static final URI DEFAULT_BASE_URL = URI.create("http://project-service");
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(3);

    private final URI baseUrl;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    public ProjectSettlementTargetClientProperties(
            String baseUrl,
            Duration connectTimeout,
            Duration readTimeout
    ) {
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
    }

    public URI baseUrl() {
        return baseUrl;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration readTimeout() {
        return readTimeout;
    }

    private static URI validateBaseUrl(String baseUrl) {
        URI uri = baseUrl == null || baseUrl.isBlank()
                ? DEFAULT_BASE_URL
                : URI.create(baseUrl);
        if (!uri.isAbsolute()
                || !("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException(
                    "Project 서비스 기본 주소는 HTTP 또는 HTTPS 절대 URI여야 합니다."
            );
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
            throw new IllegalArgumentException(
                    "Project 서비스 " + propertyName + "은 양수여야 합니다."
            );
        }
        return resolved;
    }
}
