package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 검색어 확장 — BM25/임베딩 retrieval 전용. 리랭커에는 원본 쿼리를 넘긴다(설계 §3: 확장은 recall,
 * 원본은 판단). 정적 인메모리 맵이라 LLM 없이 &lt;1ms. 매칭 키가 검색어에 포함되면 그 동의어들을
 * 원본 뒤에 공백으로 이어붙인다.
 */
@Component
public class QuerySynonymExpander {

    private static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
            Map.entry("냥이", List.of("고양이")),
            Map.entry("댕댕이", List.of("강아지")),
            Map.entry("멍멍이", List.of("강아지")),
            Map.entry("공청기", List.of("공기청정기")),
            Map.entry("폰케이스", List.of("스마트폰 케이스")),
            Map.entry("강아지", List.of("반려견", "애견")),
            Map.entry("고양이", List.of("반려묘")),
            Map.entry("빔프로젝터", List.of("프로젝터", "빔")),
            Map.entry("이어폰", List.of("무선이어폰", "블루투스 이어폰"))
    );

    public String expand(String trimmedQuery) {
        if (trimmedQuery == null || trimmedQuery.isBlank()) {
            return "";
        }
        String base = trimmedQuery.trim();
        Set<String> parts = new LinkedHashSet<>();
        parts.add(base);
        for (Map.Entry<String, List<String>> entry : SYNONYMS.entrySet()) {
            if (base.contains(entry.getKey())) {
                parts.addAll(entry.getValue());
            }
        }
        return String.join(" ", parts);
    }
}
