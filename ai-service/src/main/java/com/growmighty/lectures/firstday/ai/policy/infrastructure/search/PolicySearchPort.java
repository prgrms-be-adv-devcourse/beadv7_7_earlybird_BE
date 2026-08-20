package com.growmighty.lectures.firstday.ai.policy.infrastructure.search;

import com.growmighty.lectures.firstday.ai.policy.domain.PolicyCategory;

import java.util.List;

/**
 * 정책 인덱스에 대한 계약. Project와 달리 개별 문서 생성/수정/삭제 라이프사이클이 없다.
 * 정책 청크는 항상 정적, .md 파일에서 다시 파생되므로, 변경은 오직 전체 재색인(reindexAll)으로만 반영된다.
 */
public interface PolicySearchPort {

    /**
     * topic / content nori 키워드 매치 U embedding kNN 하이브리드 검색, category는 선택적 filter(null이면 전체 카테고리 대상).
     * 매치 없으면 빈 리스트. ES 장애 시 ServiceUnavailableException
     * search_project / search_review와 동일 컨벤션
     */
    List<PolicyChunkResult> search(String query, PolicyCategory category);

    /**
     * 전체 재색인(delete-then-insert).
     * PolicyReindexService 전용
     * 이미 임베딩까지 끝난 PolicyDocument 리스트를 통째로 받아 기존 policies 인덱스 내용을 교체한다.
     */
    void reindexAll(List<PolicyDocument> documents);
}
