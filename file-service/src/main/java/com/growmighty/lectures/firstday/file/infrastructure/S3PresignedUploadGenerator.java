package com.growmighty.lectures.firstday.file.infrastructure;

import com.growmighty.lectures.firstday.file.application.dto.PresignedUploadInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class S3PresignedUploadGenerator {
    private static final Duration UPLOAD_EXPIRATION = Duration.ofMinutes(10);
    private static final Duration DOWNLOAD_EXPIRATION = Duration.ofMinutes(5);
    private static final DateTimeFormatter KEY_DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM");
    // 안전한 확장자만 키에 반영 — originalName 을 그대로 붙이면 "a.jpg/../../secret" 같은 입력으로
    // key 에 '/' 나 '..' 가 섞여 들어갈 수 있다.
    private static final Pattern SAFE_EXTENSION = Pattern.compile("^\\.[a-zA-Z0-9]{1,10}$");

    private final S3Presigner presigner;
    private final String bucket;
    private final String cdnBaseUrl;

    public S3PresignedUploadGenerator(
            S3Presigner presigner,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.cdn-base-url}") String cdnBaseUrl
    ) {
        this.presigner = presigner;
        this.bucket = bucket;
        this.cdnBaseUrl = cdnBaseUrl;
    }

    public PresignedUploadInfo generate(Long requesterId, String contentType, String originalName) {
        String key = "files/" + requesterId + "/" + LocalDate.now().format(KEY_DATE_PATH) + "/"
            + UUID.randomUUID() + extensionOf(originalName);

        PutObjectRequest objectRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentType)
            // ponytail: presigned PUT은 S3 스펙상 업로드 용량 상한을 걸 수 없다(그러려면 presigned
            // POST 정책으로 바꿔야 함). 남용 확인되면 버킷 lifecycle/CloudFront 단에서 제한 추가.
            .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(UPLOAD_EXPIRATION)
            .putObjectRequest(objectRequest)
            .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);

        return new PresignedUploadInfo(
            presigned.url().toString(),
            cdnBaseUrl + "/" + key,
            Map.of("Content-Type", contentType)
        );
    }

    /** storedUrl(cdnBaseUrl+key)에서 key를 되짚어 5분짜리 조회용 presigned GET URL을 만든다. */
    public String presignDownload(String storedUrl) {
        String key = storedUrl.substring(cdnBaseUrl.length() + 1);

        GetObjectRequest objectRequest = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(DOWNLOAD_EXPIRATION)
            .getObjectRequest(objectRequest)
            .build();
        return presigner.presignGetObject(presignRequest).url().toString();
    }

    private String extensionOf(String originalName) {
        int dot = originalName.lastIndexOf('.');
        String extension = dot == -1 ? "" : originalName.substring(dot);
        return SAFE_EXTENSION.matcher(extension).matches() ? extension : "";
    }
}
