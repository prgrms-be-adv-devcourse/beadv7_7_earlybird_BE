package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import java.util.List;
import java.util.Map;

/**
 * 후보 projectId 목록을 사용자 원본 쿼리(확장 아님) 기준 관련도로 재정렬한다.
 * 구현체는 실패/타임아웃 시 candidateIds를 그대로 반환해야 한다 — 검색은 fusion 순서로
 * graceful degrade 하고, 화면이 멈추지 않는다.
 */
public interface Reranker {

    List<Long> rerank(String query, List<Long> candidateIds, Map<Long, ProjectDocument> docs);
}
