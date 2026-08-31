package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AttributeConflictFilterTest {

    private final AttributeConflictFilter filter = new AttributeConflictFilter();

    private ProjectDocument doc(Long id, String title, String summary) {
        return new ProjectDocument(id, title, summary, null, 1L, List.of(), null, null, null, null, null);
    }

    // ── 계절 축 ────────────────────────────────────────────────────────────────

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
    @DisplayName("운영 카탈로그의 겨울 품목(캐시미어 한복 코트/누빔 롱패딩)도 여름 검색에서 제외된다")
    void removesProductionWinterOuterwearForSummerQuery() {
        Map<Long, ProjectDocument> docs = Map.of(
                55L, doc(55L, "한파에도 끄떡없는 캐시미어 한복 코트", "캐시미어 혼방 원단"),
                56L, doc(56L, "800g 초경량 양면 두루마기 롱패딩", "5온스 고보온 누빔"),
                50L, doc(50L, "루즈핏 오가닉 코튼 티셔츠", "매일 입어도 질리지 않는 티셔츠"));

        assertThat(filter.filter("반팔 티셔츠", List.of(55L, 56L, 50L), docs)).containsExactly(50L);
    }

    // ── 반려동물 종 축 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("고양이 검색에 강아지 전용 상품이 제외되고, 종 표시 없는 소동물 상품은 남는다")
    void removesDogItemForCatQuery() {
        Map<Long, ProjectDocument> docs = Map.of(
                1L, doc(1L, "강아지 쪽쪽이 실리콘 치발기", "반려견의 이갈이 스트레스를 달래줍니다"),
                2L, doc(2L, "캣츠윈도우 접이식 창가 해먹", "흡착판으로 창문에 붙이는 고양이 해먹"),
                3L, doc(3L, "햄스터 조립식 미로 하우스", "레고처럼 조립하는 확장형 미로 하우스"));

        assertThat(filter.filter("고양이 장난감", List.of(1L, 2L, 3L), docs))
                .containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    @DisplayName("강아지·고양이 겸용 상품은 양쪽 마커를 다 갖고 있어 어느 쪽 검색에도 남는다")
    void keepsItemMarkedForBothSpecies() {
        Map<Long, ProjectDocument> docs = Map.of(
                1L, doc(1L, "반려동물 자동급식기", "강아지 고양이 모두 사용 가능"));

        assertThat(filter.filter("고양이 급식기", List.of(1L), docs)).containsExactly(1L);
        assertThat(filter.filter("강아지 급식기", List.of(1L), docs)).containsExactly(1L);
    }

    // ── 성별 축 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("남성 검색에 여성 전용 상품은 제외되고 남녀공용 상품은 남는다")
    void removesWomenOnlyItemForMenQuery() {
        Map<Long, ProjectDocument> docs = Map.of(
                48L, doc(48L, "여성 프리미엄 올인원 맨투맨", "여성 전용 올인원 맨투맨/후드티"),
                45L, doc(45L, "플러스사이즈 맨투맨/후드티", "남녀공용 유니섹스 맨투맨"),
                33L, doc(33L, "넓은 어깨 머슬핏 티셔츠", "남성의 선택, 데일리웨어"));

        assertThat(filter.filter("남성 맨투맨", List.of(48L, 45L, 33L), docs))
                .containsExactlyInAnyOrder(45L, 33L);
    }

    // ── 중립/방어 ──────────────────────────────────────────────────────────────

    @Test
    void keepsAllWhenQueryHasNoAxisSignal() {
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
    @DisplayName("마커가 다른 단어에 substring으로 섞여도 오탐하지 않는다")
    void doesNotMatchMarkerHiddenInUnrelatedWord() {
        Map<Long, ProjectDocument> docs = Map.of(
                76L, doc(76L, "개구리 만두", "개구리 만두입니다"));

        assertThat(filter.filter("고양이 간식", List.of(76L), docs)).containsExactly(76L);
    }

    @Test
    void keepsCandidateWithNoDocument() {
        assertThat(filter.filter("여름 옷", List.of(99L), Map.of())).containsExactly(99L);
    }
}
