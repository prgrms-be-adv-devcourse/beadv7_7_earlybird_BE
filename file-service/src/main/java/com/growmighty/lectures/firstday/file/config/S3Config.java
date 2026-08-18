package com.growmighty.lectures.firstday.file.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/** 자격증명은 AWS SDK 기본 체인(환경변수/인스턴스 프로필 등)을 그대로 사용한다. */
@Configuration
public class S3Config {

    @Bean
    public S3Presigner s3Presigner(
            @Value("${aws.s3.region}") String region,
            // 비어있으면 실제 AWS S3. 값이 있으면 MinIO 등 S3 호환 스토리지의 엔드포인트로 그쪽에 서명한다.
            @Value("${aws.s3.endpoint-override:}") String endpointOverride) {
        S3Presigner.Builder builder = S3Presigner.builder().region(Region.of(region));
        if (!endpointOverride.isBlank()) {
            builder.endpointOverride(URI.create(endpointOverride))
                // MinIO 등은 버킷별 서브도메인(virtual-hosted style)을 DNS로 해석해줄 수 없으니
                // path-style(host/bucket/key)을 강제한다.
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }
}
