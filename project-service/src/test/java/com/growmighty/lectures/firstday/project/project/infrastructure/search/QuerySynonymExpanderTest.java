package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuerySynonymExpanderTest {

    private final QuerySynonymExpander expander = new QuerySynonymExpander();

    @Test
    void appendsSynonymsForMatchedTerm() {
        assertThat(expander.expand("강아지 옷"))
                .contains("강아지 옷")
                .contains("반려견")
                .contains("애견");
    }

    @Test
    void expandsSlang() {
        assertThat(expander.expand("댕댕이 간식")).contains("강아지");
        assertThat(expander.expand("냥이 장난감")).contains("고양이");
    }

    @Test
    void noMatchReturnsOriginal() {
        assertThat(expander.expand("노트북 파우치")).isEqualTo("노트북 파우치");
    }

    @Test
    void nullOrBlankReturnsEmpty() {
        assertThat(expander.expand(null)).isEmpty();
        assertThat(expander.expand("  ")).isEmpty();
    }
}
