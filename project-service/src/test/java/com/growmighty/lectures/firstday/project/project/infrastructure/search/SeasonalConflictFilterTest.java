package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SeasonalConflictFilterTest {

    private final SeasonalConflictFilter filter = new SeasonalConflictFilter();

    private ProjectDocument doc(Long id, String title, String summary) {
        return new ProjectDocument(id, title, summary, null, 1L, List.of(), null, null, null, null, null);
    }

    @Test
    void removesWinterItemForSummerQuery() {
        Map<Long, ProjectDocument> docs = Map.of(
                1L, doc(1L, "여름 린넨 반팔 티셔츠", "시원한 여름용"),
                2L, doc(2L, "울 혼방 롱코트", "보온성 높은 겨울 방한 코트"));

        assertThat(filter.filter("여름에 입기 좋은 옷", List.of(1L, 2L), docs)).containsExactly(1L);
    }

    @Test
    void removesSummerItemForWinterQuery() {
        Map<Long, ProjectDocument> docs = Map.of(
                1L, doc(1L, "겨울 패딩 점퍼", "한겨울 방한"),
                2L, doc(2L, "쿨링 반팔 티셔츠", "여름 시원한 린넨"));

        assertThat(filter.filter("겨울에 따뜻한 옷", List.of(1L, 2L), docs)).containsExactly(1L);
    }

    @Test
    void keepsAllWhenQuerySeasonUnclear() {
        Map<Long, ProjectDocument> docs = Map.of(
                1L, doc(1L, "울 혼방 롱코트", "겨울 방한"),
                2L, doc(2L, "반팔 티셔츠", "여름 쿨링"));

        assertThat(filter.filter("옷 추천", List.of(1L, 2L), docs)).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void keepsDocWithoutExplicitOppositeMarker() {
        Map<Long, ProjectDocument> docs = Map.of(
                1L, doc(1L, "베이직 코튼 티셔츠", "사계절 데일리"));

        assertThat(filter.filter("여름 옷", List.of(1L), docs)).containsExactly(1L);
    }

    @Test
    void keepsCandidateWithNoDocument() {
        assertThat(filter.filter("여름 옷", List.of(99L), Map.of())).containsExactly(99L);
    }
}
