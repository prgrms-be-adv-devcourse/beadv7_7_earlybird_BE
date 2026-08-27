package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 강한 계절 충돌만 하드 제외 — 리랭커의 안전망(설계 §5). 대부분의 판단은 리랭커가 하고,
 * 이 필터는 "여름 옷 검색에 겨울 롱코트"처럼 명백한 케이스가 나쁜 rerank 콜에도 절대 안 뜨게 보장한다.
 *
 * <p>제외 조건 = AND 둘 다:
 * <ol>
 *   <li>쿼리가 계절을 명확히 함의 (여름/겨울 키워드 중 한쪽만)</li>
 *   <li>문서 title/summary에 반대 계절의 강한 마커가 명시됨</li>
 * </ol>
 * 애매하면(쿼리에 계절 신호가 없거나 양쪽 다) 아무것도 안 한다.
 */
@Slf4j
@Component
public class SeasonalConflictFilter {

    private static final Set<String> SUMMER_QUERY = Set.of("여름", "여름용", "하절기", "쿨링", "시원한");
    private static final Set<String> WINTER_QUERY = Set.of("겨울", "겨울용", "동절기", "방한", "보온", "따뜻한");

    private static final Set<String> SUMMER_DOC_MARKERS = Set.of("여름", "쿨링", "린넨", "반팔", "냉감", "시어서커");
    private static final Set<String> WINTER_DOC_MARKERS =
            Set.of("겨울", "방한", "기모", "롱코트", "패딩", "니트", "다운", "플리스", "한파", "혹한");

    private enum Season { SUMMER, WINTER, NONE }

    public List<Long> filter(String originalQuery, List<Long> candidateIds, Map<Long, ProjectDocument> docs) {
        Season querySeason = querySeason(originalQuery);
        if (querySeason == Season.NONE) {
            return candidateIds;
        }
        Set<String> oppositeMarkers =
                (querySeason == Season.SUMMER) ? WINTER_DOC_MARKERS : SUMMER_DOC_MARKERS;

        List<Long> kept = new ArrayList<>(candidateIds.size());
        for (Long id : candidateIds) {
            ProjectDocument doc = docs.get(id);
            if (doc != null && hasMarker(doc, oppositeMarkers)) {
                log.info("[SeasonalFilter] 계절 충돌 후보 제외: projectId={}, title='{}', querySeason={}",
                        id, doc.title(), querySeason);
                continue;
            }
            kept.add(id);
        }
        return kept;
    }

    private Season querySeason(String query) {
        if (query == null) {
            return Season.NONE;
        }
        boolean summer = SUMMER_QUERY.stream().anyMatch(query::contains);
        boolean winter = WINTER_QUERY.stream().anyMatch(query::contains);
        if (summer == winter) {
            return Season.NONE;
        }
        return summer ? Season.SUMMER : Season.WINTER;
    }

    private boolean hasMarker(ProjectDocument doc, Set<String> markers) {
        String text = (doc.title() == null ? "" : doc.title())
                + " " + (doc.summary() == null ? "" : doc.summary());
        return markers.stream().anyMatch(text::contains);
    }
}
