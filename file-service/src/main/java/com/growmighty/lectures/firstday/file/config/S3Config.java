package com.growmighty.lectures.firstday.file.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** 자격증명은 AWS SDK 기본 체인(환경변수/인스턴스 프로필 등)을 그대로 사용한다. */
@Configuration
public class S3Config {

    @Bean
    public S3Presigner s3Presigner(@Value("${aws.s3.region}") String region) {
        return S3Presigner.builder()
            .region(Region.of(region))
            .build();
    }
}
