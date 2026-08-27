package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import java.util.List;

/**
 * 사용자 검색어에서 추출된 범용 요구사항/제약조건 단위.
 *
 * <p>특정 속성(season, material 등)에 국한되지 않고 자연어 검색어에서 요구하는
 * 모든 조건(용도, 특성, 대상, 크기/무게, 상황 등)을 범용적으로 표현한다.
 *
 * @param text               요구사항 텍스트 (예: "여름용", "가벼운", "조용한", "초보자용", "원룸용", "휴대하기 좋은")
 * @param type               요구사항 유형 (예: "usage_or_context", "characteristic", "target_audience", "size_or_weight", "preference")
 * @param isStrict           필수적인 제약조건인지 여부 (true: 강한 제약, false: 선호)
 * @param polarOpposites     해당 요구사항과 명확히 충돌/대립되는 의미적 반대 표현 목록 (예: "여름용" -> ["겨울", "방한", "기모", "두꺼운 패딩", "롱코트"])
 */
public record Requirement(
        String text,
        String type,
        boolean isStrict,
        List<String> polarOpposites
) {
    public Requirement(String text, String type, boolean isStrict) {
        this(text, type, isStrict, List.of());
    }

    public Requirement(String text, String type) {
        this(text, type, false, List.of());
    }
}
