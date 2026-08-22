package com.growmighty.lectures.firstday.board.feign.httpClient.file;

import com.growmighty.lectures.firstday.board.feign.httpClient.file.dto.FileBatchApiData;
import com.growmighty.lectures.firstday.board.feign.port.FilePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Component
@RequiredArgsConstructor
public class FileHttpClient implements FilePort {

    private static final String REVIEW_OWNER_TYPE = "REVIEW";

    private final FileFeignClient fileFeignClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Override
    public Map<Long, List<String>> getReviewPhotoUrls(List<Long> reviewIds) {
        if (reviewIds.isEmpty()) {
            return Map.of();
        }
        return circuitBreakerFactory.create("file").run(
            () -> fetch(reviewIds),
            cause -> fallback(reviewIds, cause)
        );
    }

    private Map<Long, List<String>> fetch(List<Long> reviewIds) {
        List<FileBatchApiData> files = fileFeignClient.getFilesByOwners(REVIEW_OWNER_TYPE, reviewIds).data();
        return files.stream().collect(Collectors.groupingBy(
            FileBatchApiData::ownerId,
            Collectors.mapping(FileBatchApiData::storedUrl, Collectors.toList())
        ));
    }

    private Map<Long, List<String>> fallback(List<Long> reviewIds, Throwable cause) {
        log.warn("리뷰 첨부 사진 배치 조회 실패, 빈 목록으로 대체. reviewIds={}, 원인={}", reviewIds, cause.toString());
        return Map.of();
    }
}
