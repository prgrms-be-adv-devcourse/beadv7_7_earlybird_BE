package com.growmighty.lectures.firstday.ai.policy.infrastructure.embedding;

import com.growmighty.lectures.firstday.ai.policy.infrastructure.loader.PolicyChunk;
import com.growmighty.lectures.firstday.ai.policy.infrastructure.search.PolicyDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyEmbeddingService {

    private static final int MAX_QUERY_LENGTH = 2000;

    private final EmbeddingModel embeddingModel;

    public List<PolicyDocument> embedAll(List<PolicyChunk> chunks) {
        return chunks.stream()
            .map(this::embed)
            .toList();
    }

    private PolicyDocument embed(PolicyChunk chunk) {
        float[] embedding = embeddingModel.embed(chunk.content());
        return new PolicyDocument(chunk.chunkId(), chunk.category(), chunk.topic(), chunk.content(), embedding);
    }

    public float[] embedQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String target = query.trim();
        if (target.length() > MAX_QUERY_LENGTH) {
            target = target.substring(0, MAX_QUERY_LENGTH);
        }
        try {
            return embeddingModel.embed(target);
        } catch (Exception e) {
            log.warn("정책 검색 쿼리 임베딩 실패. query_length={}, 원인:{}", target.length(), e.getMessage());
            return null;
        }
    }
}
