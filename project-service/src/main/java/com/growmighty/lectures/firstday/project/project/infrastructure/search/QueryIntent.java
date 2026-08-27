package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import java.util.List;

/**
 * LLM이 사용자 검색어에서 구조화하여 추출한 검색 의도(Query Intent).
 *
 * <h3>설계 원칙</h3>
 * <ul>
 *   <li>검색어에 명시되거나 의미적으로 강하게 추론 가능한 정보만 채운다.</li>
 *   <li>확실하지 않은 필드는 {@code null}로 둔다 — 없는 정보를 만들어 내지 않는다.</li>
 *   <li>조건의 존재 여부를 3단계로 구분한다: 명확 관련 / 명확 충돌 / 판단 불가(null).</li>
 * </ul>
 *
 * <h3>사용 위치</h3>
 * <ul>
 *   <li>{@link QueryIntentAnalyzer}가 생성한다.</li>
 *   <li>{@code enrichedQuery}는 Vector Search(kNN) 임베딩에만 사용한다.</li>
 *   <li>BM25는 항상 원본 검색어를 사용한다 — {@code enrichedQuery}로 대체하지 않는다.</li>
 * </ul>
 *
 * @param productType     상품 유형 (예: "백팩", "셔츠", "공기청정기"). 명확하지 않으면 null.
 * @param season          계절 조건 (예: "여름", "겨울"). 명확하지 않으면 null.
 * @param color           색상 조건 (예: "검정", "흰색"). 명확하지 않으면 null.
 * @param purpose         용도·상황 (예: "캠핑", "산책", "우천", "선물"). 명확하지 않으면 null.
 * @param targetUser      대상·수요자 (예: "여성", "강아지", "1인"). 명확하지 않으면 null.
 * @param material        소재·재질 (예: "가죽", "방수", "천연"). 명확하지 않으면 null.
 * @param hardConstraints 검색어에 명확히 명시된 필수 조건. 불일치 시 결과 품질을 크게 낮춰야 하는 조건.
 *                        (예: ["여름용", "방수", "여성용"])
 * @param softPreferences 정량적 필수 조건이 아닌 선호·의도 표현. 만족 시 순위를 높이는 데 활용.
 *                        (예: ["편한", "가벼운", "감성적인"])
 * @param enrichedQuery   Vector Search 임베딩에 사용할 의미 보강 쿼리 텍스트.
 *                        원본 자연어보다 상품 설명에 가까운 구체적 표현으로 재구성한다.
 *                        (예: "여름 쿨링 반팔 티셔츠 여성 의류" / "강아지 산책용 방수 가방 백팩")
 *                        LLM 실패 시 원본 검색어가 그대로 사용된다.
 */
public record QueryIntent(
        String productType,
        String season,
        String color,
        String purpose,
        String targetUser,
        String material,
        List<String> hardConstraints,
        List<String> softPreferences,
        String enrichedQuery
) {

    /**
     * LLM 호출 실패 또는 짧은 단일 키워드처럼 분석이 불필요할 때 사용하는 패스스루 인스턴스.
     * {@code enrichedQuery}가 원본 쿼리와 동일하므로 기존 동작과 완전히 동일하게 작동한다.
     */
    public static QueryIntent passThrough(String originalQuery) {
        return new QueryIntent(
                null, null, null, null, null, null,
                List.of(), List.of(),
                originalQuery
        );
    }

    /**
     * enrichedQuery가 실제로 원본 쿼리에 의미를 추가했는지 여부.
     * 단순 패스스루 인스턴스와의 구별에 사용한다.
     */
    public boolean hasStructuredIntent() {
        return productType != null || season != null || color != null
                || purpose != null || targetUser != null || material != null
                || !hardConstraints.isEmpty();
    }
}
