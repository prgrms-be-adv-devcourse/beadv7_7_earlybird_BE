package com.growmighty.lectures.firstday.ai.tool.feign.httpClient.review;

import com.growmighty.lectures.firstday.ai.tool.feign.httpClient.review.dto.ReviewSearchApiData;
import com.growmighty.lectures.firstday.ai.tool.feign.port.ReviewSearchPort;
import com.growmighty.lectures.firstday.ai.tool.feign.port.dto.ReviewSearchResult;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewSearchHttpClient implements ReviewSearchPort {

    private static final int REVIEW_CEILING = 100;

    private final ReviewSearchFeignClient reviewSearchFeignClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Override
    public List<ReviewSearchResult> search(Long projectId) {
        return circuitBreakerFactory.create("review").run(
            () -> fetch(projectId),
            cause -> failHard(projectId, cause)
        );
    }

    private List<ReviewSearchResult> fetch(Long projectId) {
        List<ReviewSearchApiData> data = reviewSearchFeignClient.search(projectId).data();
        return data.stream()
            .limit(REVIEW_CEILING)
            .map(this::toResult)
            .toList();
    }

    private List<ReviewSearchResult> failHard(Long projectId, Throwable cause) {
        log.warn("리뷰 조회 실패. projectId={}, 원인={}", projectId, cause.toString());
        throw new ServiceUnavailableException("리뷰 조회를 처리할 수 없습니다. projectId=" + projectId);
    }

    private ReviewSearchResult toResult(ReviewSearchApiData data) {
        return new ReviewSearchResult(
            data.id(),
            data.projectId(),
            data.rewardId(),
            data.rewardName(),
            data.authorName(),
            data.rating(),
            data.content(),
            data.createdAt()
        );
    }
}
