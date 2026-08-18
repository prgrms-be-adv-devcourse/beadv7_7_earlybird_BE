package com.growmighty.lectures.firstday.file.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class S3ConfigTest {

    private final S3Config config = new S3Config();

    // S3Config는 자격증명 파라미터를 안 받고 SDK 기본 체인을 쓴다(운영 의도) — 테스트 환경엔 실
    // 자격증명이 없을 수 있어, 서명 계산에 필요한 만큼만 시스템 프로퍼티로 임시 자격증명을 준다.
    @BeforeEach
    void stubCredentials() {
        System.setProperty("aws.accessKeyId", "dummy");
        System.setProperty("aws.secretAccessKey", "dummy");
    }

    @AfterEach
    void clearCredentials() {
        System.clearProperty("aws.accessKeyId");
        System.clearProperty("aws.secretAccessKey");
    }

    @Test
    void endpoint_override가_없으면_AWS_S3_기본_virtual_hosted_style을_쓴다() {
        S3Presigner presigner = config.s3Presigner("ap-northeast-2", "");

        String url = presign(presigner).url().toString();

        assertThat(url).startsWith("https://earlybird-files.s3.ap-northeast-2.amazonaws.com/");
    }

    @Test
    void endpoint_override가_있으면_그_호스트에_path_style로_서명한다() {
        S3Presigner presigner = config.s3Presigner("ap-northeast-2", "https://files.example.com");

        String url = presign(presigner).url().toString();

        assertThat(url).startsWith("https://files.example.com/earlybird-files/");
    }

    private software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest presign(S3Presigner presigner) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
            .bucket("earlybird-files")
            .key("files/1/2026/08/uuid.jpg")
            .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(10))
            .putObjectRequest(objectRequest)
            .build();
        return presigner.presignPutObject(presignRequest);
    }
}
