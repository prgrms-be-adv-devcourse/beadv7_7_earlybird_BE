package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * {@code cohere.rerank.enabled=false}(또는 미설정)일 때 활성.
 * 재정렬 없이 fusion 순서를 그대로 쓴다 — CI, 부하 테스트, Cohere 미설정 환경용.
 */
@Component
@ConditionalOnProperty(prefix = "cohere.rerank", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpReranker implements Reranker {

    @Override
    public List<Long> rerank(String query, List<Long> candidateIds, Map<Long, ProjectDocument> docs) {
        return candidateIds;
    }
}
