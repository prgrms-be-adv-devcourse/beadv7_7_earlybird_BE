package com.growmighty.lectures.firstday.ai.policy.application;

import com.growmighty.lectures.firstday.ai.policy.infrastructure.embedding.PolicyEmbeddingService;
import com.growmighty.lectures.firstday.ai.policy.infrastructure.loader.PolicyChunk;
import com.growmighty.lectures.firstday.ai.policy.infrastructure.loader.PolicyDocumentLoader;
import com.growmighty.lectures.firstday.ai.policy.infrastructure.search.PolicyDocument;
import com.growmighty.lectures.firstday.ai.policy.infrastructure.search.PolicySearchPort;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

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
    }
}
