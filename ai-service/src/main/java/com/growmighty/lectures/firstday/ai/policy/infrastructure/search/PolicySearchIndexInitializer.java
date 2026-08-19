package com.growmighty.lectures.firstday.ai.policy.infrastructure.search;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PolicySearchIndexInitializer {

    private final ElasticsearchOperations elasticsearchOperations;

    @PostConstruct
    void ensureIndexExists() {
        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(PolicyDocument.class);
            if (!indexOps.exists()) {
                indexOps.createWithMapping();
            }
        } catch (RuntimeException e) {
            log.warn("정책 검색 인덱스 초기화 실패 - 검색 기능이 정상 동작하지 않을 수 있습니다.", e);
        }
    }
}
