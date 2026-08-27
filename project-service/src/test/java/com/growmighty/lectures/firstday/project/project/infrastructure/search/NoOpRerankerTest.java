package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpRerankerTest {

    @Test
    void returnsCandidatesUnchanged() {
        Reranker reranker = new NoOpReranker();
        List<Long> ids = List.of(3L, 1L, 2L);

        assertThat(reranker.rerank("강아지 옷", ids, Map.of())).isEqualTo(ids);
    }
}
