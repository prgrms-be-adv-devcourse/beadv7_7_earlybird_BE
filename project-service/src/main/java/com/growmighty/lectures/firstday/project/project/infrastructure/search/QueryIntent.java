package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import java.util.List;

/**
 * LLM이 사용자 검색어에서 구조화하여 추출한 검색 의도(Query Intent).
 *
 * <h3>설계 원칙</h3>
 * <ul>
 *   <li>검색 대상(target)과 범용적 요구사항(requirements) 구조로 추출한다.</li>
 *   <li>특정 고정 속성(season, material 등)에 국한되지 않고 일반화된 자연어 의미를 담는다.</li>
 *   <li>기존 필드(productType, season 등)는 점진적 마이그레이션 및 하위 호환성을 위해 보존한다.</li>
 * </ul>
 *
 * @param target          검색 핵심 대상 품목 (예: "옷", "가방", "키보드", "카메라", "책상", "노트북"). 명확하지 않으면 null.
 * @param requirements    검색어가 요구하는 범용 조건/제약 목록 (예: [Requirement("여름용", "usage_or_context", true)])
 * @param productType     하위 호환성용 상품 유형 (target과 동일)
 * @param season          하위 호환성용 계절 조건
 * @param color           하위 호환성용 색상 조건
 * @param purpose         하위 호환성용 용도·상황
 * @param targetUser      하위 호환성용 대상·수요자
 * @param material        하위 호환성용 소재·재질
 * @param hardConstraints 하위 호환성용 필수 조건 목록
 * @param softPreferences 하위 호환성용 선호 조건 목록
 * @param enrichedQuery   Vector Search(kNN) 임베딩에 사용할 의미 보강 쿼리 텍스트
 */
public record QueryIntent(
        String target,
        List<Requirement> requirements,
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

    /** 기존 생성자 호환성 유지용 생성자 */
    public QueryIntent(
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
        this(
                productType,
                buildRequirements(season, color, purpose, targetUser, material, hardConstraints, softPreferences),
                productType, season, color, purpose, targetUser, material,
                hardConstraints != null ? hardConstraints : List.of(),
                softPreferences != null ? softPreferences : List.of(),
                enrichedQuery
        );
    }

    /**
     * LLM 호출 실패 또는 짧은 단일 키워드처럼 분석이 불필요할 때 사용하는 패스스루 인스턴스.
     * {@code enrichedQuery}가 원본 쿼리와 동일하므로 기존 동작과 완전히 동일하게 작동한다.
     */
    public static QueryIntent passThrough(String originalQuery) {
        String safeQuery = originalQuery != null ? originalQuery : "";
        return new QueryIntent(
                null,
                List.of(),
                null, null, null, null, null, null,
                List.of(), List.of(),
                safeQuery
        );
    }

    /**
     * enrichedQuery가 실제로 원본 쿼리에 의미를 추가했는지 또는 요구사항이 존재하는지 여부.
     */
    public boolean hasStructuredIntent() {
        return (target != null && !target.isBlank())
                || (requirements != null && !requirements.isEmpty())
                || productType != null || season != null || color != null
                || purpose != null || targetUser != null || material != null
                || (hardConstraints != null && !hardConstraints.isEmpty());
    }

    public boolean hasRequirements() {
        return requirements != null && !requirements.isEmpty();
    }

    private static List<Requirement> buildRequirements(
            String season, String color, String purpose, String targetUser, String material,
            List<String> hardConstraints, List<String> softPreferences
    ) {
        java.util.List<Requirement> reqs = new java.util.ArrayList<>();
        if (season != null && !season.isBlank()) {
            reqs.add(new Requirement(season, "season", true));
        }
        if (color != null && !color.isBlank()) {
            reqs.add(new Requirement(color, "color", true));
        }
        if (purpose != null && !purpose.isBlank()) {
            reqs.add(new Requirement(purpose, "purpose", false));
        }
        if (targetUser != null && !targetUser.isBlank()) {
            reqs.add(new Requirement(targetUser, "targetUser", false));
        }
        if (material != null && !material.isBlank()) {
            reqs.add(new Requirement(material, "material", true));
        }
        if (hardConstraints != null) {
            for (String hc : hardConstraints) {
                if (hc != null && !hc.isBlank() && reqs.stream().noneMatch(r -> r.text().equalsIgnoreCase(hc))) {
                    reqs.add(new Requirement(hc, "constraint", true));
                }
            }
        }
        if (softPreferences != null) {
            for (String sp : softPreferences) {
                if (sp != null && !sp.isBlank() && reqs.stream().noneMatch(r -> r.text().equalsIgnoreCase(sp))) {
                    reqs.add(new Requirement(sp, "preference", false));
                }
            }
        }
        return List.copyOf(reqs);
    }
}
