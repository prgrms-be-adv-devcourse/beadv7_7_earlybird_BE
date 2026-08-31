package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 명백한 속성 충돌만 하드 제외 — 리랭커의 안전망(설계 §5). 대부분의 판단은 리랭커가 하고,
 * 이 필터는 "여름 옷 검색에 겨울 롱코트", "고양이 장난감 검색에 강아지 장난감"처럼
 * 누가 봐도 틀린 후보가 나쁜 rerank 콜에도 절대 안 뜨게 보장한다.
 *
 * <p>축(axis)은 서로 배타적인 두 진영(A/B)으로 정의한다. 제외 조건 = AND 셋 다:
 * <ol>
 *   <li>쿼리가 한 진영만 명확히 함의 (양쪽 다거나 아무것도 없으면 그 축은 통과)</li>
 *   <li>문서 title/summary에 반대 진영의 강한 마커가 명시됨</li>
 *   <li>문서에 같은 편 마커는 없음 (양쪽 다 있으면 애매 → 살림. 예: "남녀공용", 강아지·고양이 겸용)</li>
 * </ol>
 * 마커는 substring 매치라 다른 단어에 섞여 들어가지 않는 어휘만 넣는다
 * (예: "개"는 개구리·소개·공개에, "견"은 발견·의견에 걸려서 제외).
 * 축은 운영 서버의 실제 카탈로그에서 오탐이 확인된 것만 추가한다 — 리랭커가 이미 하는 판단을
 * 여기에 중복으로 하드코딩하면 리랭커가 옳을 때조차 결과를 버리게 된다.
 */
@Slf4j
@Component
public class AttributeConflictFilter {

    private record Axis(String name,
                        Set<String> queryA, Set<String> queryB,
                        Set<String> markersA, Set<String> markersB) {
    }

    private static final List<Axis> AXES = List.of(
            // 계절: 한여름/한겨울 의류가 서로의 검색 결과에 뜨는 것을 막는다.
            // 운영 카탈로그 겨울 품목 — 기모 후드티(#22), 캐시미어 한복 코트(#55), 누빔 롱패딩(#56), 겨울 장갑(#62)
            new Axis("season",
                    Set.of("여름", "여름용", "하절기", "쿨링", "시원한", "반팔", "민소매"),
                    Set.of("겨울", "겨울용", "동절기", "방한", "보온", "따뜻한", "한파", "혹한"),
                    Set.of("여름", "쿨링", "린넨", "반팔", "냉감", "시어서커"),
                    Set.of("겨울", "방한", "기모", "롱코트", "패딩", "니트", "다운", "플리스", "한파", "혹한",
                            "캐시미어", "누빔")),

            // 반려동물 종: 카탈로그의 반려동물 프로젝트가 강아지 쪽에 쏠려 있어(#8 #17 #30 #39 #47 #57 #69)
            // "고양이 장난감" 검색에 강아지 장난감이 그대로 올라오는 게 가장 잦은 오탐이었다.
            // 햄스터·소동물(#59)은 어느 쪽 마커도 없어 양쪽 검색 모두에 남는다.
            new Axis("petSpecies",
                    Set.of("강아지", "댕댕이", "멍멍이", "반려견", "애견"),
                    Set.of("고양이", "냥이", "반려묘", "캣"),
                    Set.of("강아지", "댕댕이", "멍멍이", "반려견", "애견"),
                    Set.of("고양이", "냥이", "반려묘", "캣")),

            // 성별: 카탈로그 의류가 여성 전용(#48 #55)·남성 전용(#33 #75)·남녀공용(#45 #56 #74)으로
            // 명시돼 있다. 남녀공용/유니섹스 문서는 어느 쪽 마커도 없어 양쪽 검색에 모두 남는다.
            new Axis("gender",
                    Set.of("남성", "남자", "맨즈"),
                    Set.of("여성", "여자", "우먼"),
                    Set.of("남성", "남자", "맨즈"),
                    Set.of("여성", "여자", "우먼"))
    );

    public List<Long> filter(String originalQuery, List<Long> candidateIds, Map<Long, ProjectDocument> docs) {
        List<Long> kept = new ArrayList<>(candidateIds.size());
        for (Long id : candidateIds) {
            ProjectDocument doc = docs.get(id);
            Axis conflict = (doc == null) ? null : conflictingAxis(originalQuery, doc);
            if (conflict != null) {
                log.info("[ConflictFilter] 속성 충돌 후보 제외: axis={}, projectId={}, title='{}'",
                        conflict.name(), id, doc.title());
                continue;
            }
            kept.add(id);
        }
        return kept;
    }

    private Axis conflictingAxis(String query, ProjectDocument doc) {
        String text = searchableText(doc);
        for (Axis axis : AXES) {
            boolean queryA = containsAny(query, axis.queryA());
            boolean queryB = containsAny(query, axis.queryB());
            if (queryA == queryB) {
                continue; // 쿼리가 이 축에 대해 중립 — 판단 안 함
            }
            boolean docA = containsAny(text, axis.markersA());
            boolean docB = containsAny(text, axis.markersB());
            if (docA == docB) {
                continue; // 문서가 양쪽 다거나 아무 표시 없음 — 애매하면 살린다
            }
            if (queryA == docB) {
                return axis;
            }
        }
        return null;
    }

    private String searchableText(ProjectDocument doc) {
        return (doc.title() == null ? "" : doc.title())
                + " " + (doc.summary() == null ? "" : doc.summary());
    }

    private boolean containsAny(String text, Set<String> terms) {
        return text != null && terms.stream().anyMatch(text::contains);
    }
}
