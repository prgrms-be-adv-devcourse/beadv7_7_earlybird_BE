package com.growmighty.lectures.firstday.ai.tool.feign.httpClient.file;

import com.growmighty.lectures.firstday.ai.tool.feign.httpClient.file.dto.FileApiData;
import com.growmighty.lectures.firstday.ai.tool.feign.port.file.FileLookupPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileHttpClient implements FileLookupPort {

    private static final String PROJECT_OWNER_TYPE = "PROJECT";

    private final FileFeignClient fileFeignClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Override
    public String findThumbnailUrl(Long projectId, Long thumbnailId) {
        return circuitBreakerFactory.create("fileLookup").run(
            () -> fetch(projectId, thumbnailId),
            cause -> fallback(projectId, cause)
        );
    }

    private String fetch(Long projectId, Long thumbnailId) {
        List<FileApiData> files = fileFeignClient.findByOwner(PROJECT_OWNER_TYPE, projectId).data();
        return files.stream()
            .filter(file -> file.id().equals(thumbnailId))
            .map(FileApiData::storedUrl)
            .findFirst()
            .orElse(null);
    }

    private String fallback(Long projectId, Throwable cause) {
        log.warn("썸네일 URL 조회 실패. projectId={}, 원인 ={}", projectId, cause.toString());
        return null;
    }
}
