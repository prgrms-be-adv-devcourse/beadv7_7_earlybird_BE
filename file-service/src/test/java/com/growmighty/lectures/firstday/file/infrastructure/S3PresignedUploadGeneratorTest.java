package com.growmighty.lectures.firstday.file.infrastructure;

import com.growmighty.lectures.firstday.file.application.dto.PresignedUploadInfo;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

class S3PresignedUploadGeneratorTest {

    // presignPutObject 는 로컬 SigV4 서명 계산만 하고 네트워크 호출을 하지 않으므로 더미 자격증명으로 충분하다.
    private final S3Presigner presigner = S3Presigner.builder()
        .region(Region.AP_NORTHEAST_2)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")))
        .build();

    private final S3PresignedUploadGenerator generator =
        new S3PresignedUploadGenerator(presigner, "earlybird-files", "https://cdn.example.com");

    @Test
    void 요청자ID와_확장자를_포함한_날짜경로_키로_presign_결과를_생성한다() {
        PresignedUploadInfo info = generator.generate(42L, "image/jpeg", "thumb.jpg");

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        assertThat(info.storedUrl()).startsWith("https://cdn.example.com/files/42/" + datePath + "/");
        assertThat(info.storedUrl()).endsWith(".jpg");
        assertThat(info.uploadUrl()).contains("earlybird-files");
        assertThat(info.requiredHeaders()).containsEntry("Content-Type", "image/jpeg");
    }

    @Test
    void 원본파일명에_확장자가_없으면_확장자없이_키를_만든다() {
        PresignedUploadInfo info = generator.generate(42L, "application/octet-stream", "noext");

        assertThat(info.storedUrl()).doesNotContain(".noext");
        assertThat(info.storedUrl()).matches(".*/[0-9a-fA-F-]{36}$");
    }

    @Test
    void 확장자에_경로_구분자나_점이_섞여있으면_확장자를_버린다() {
        PresignedUploadInfo info = generator.generate(42L, "image/jpeg", "a.jpg/../../secret");

        assertThat(info.storedUrl()).doesNotContain("..");
        assertThat(info.storedUrl()).matches(".*/[0-9a-fA-F-]{36}$");
    }
}
