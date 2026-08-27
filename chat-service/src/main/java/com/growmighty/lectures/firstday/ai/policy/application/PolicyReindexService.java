package com.growmighty.lectures.firstday.ai.policy.application;

import com.growmighty.lectures.firstday.ai.policy.infrastructure.embedding.PolicyEmbeddingService;
import com.growmighty.lectures.firstday.ai.policy.infrastructure.loader.PolicyChunk;
import com.growmighty.lectures.firstday.ai.policy.infrastructure.loader.PolicyDocumentLoader;
import com.growmighty.lectures.firstday.ai.policy.infrastructure.search.PolicyDocument;
import com.growmighty.lectures.firstday.ai.policy.infrastructure.search.PolicySearchPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyReindexService {

    private final PolicyDocumentLoader documentLoader;
    private final PolicyEmbeddingService embeddingService;
    private final PolicySearchPort searchPort;

    @Async
    public void reindexAll() {
        List<PolicyChunk> chunks = documentLoader.loadAll();
        List<PolicyDocument> documents = embeddingService.embedAll(chunks);
        searchPort.reindexAll(documents);
        long documentCount = chunks.stream().map(PolicyChunk::fileSlug).distinct().count();
        log.info("정책 문서 재색인 완료. 총 {}개 문서, {}개 청크 색인", documentCount, documents.size());
    }
}
