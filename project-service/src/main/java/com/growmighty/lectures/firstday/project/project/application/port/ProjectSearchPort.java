package com.growmighty.lectures.firstday.project.project.application.port;

import com.growmighty.lectures.firstday.project.project.domain.Project;

import java.util.List;

/**
 * ES 검색 인덱스에 대한 계약. index/remove/search 모두 실패해도 예외를 던지지 않는다(호출부의
 * MySQL 트랜잭션·응답에 영향을 주면 안 되므로 — 구현체가 내부에서 흡수). search는 ES 장애 시
 * DB title LIKE 검색으로 강등한다(Graceful Degradation) — 벡터/nori 매치보다 품질은 떨어지지만
 * 화면이 완전히 멈추는 것보다 낫다.
 */
public interface ProjectSearchPort {

    /** 색인 실패는 로그만 남기고 삼킨다 — 절대 예외를 던지지 않는다. */
    void index(Project project);

    /** 삭제 실패는 로그만 남기고 삼킨다 — 절대 예외를 던지지 않는다. */
    void remove(Long projectId);

    /**
     * nori 키워드 매치 ∪ 임베딩 kNN 하이브리드 검색. 매치 없으면 빈 리스트.
     * ES 장애 시 DB title LIKE '%keyword%' 검색으로 강등해 후보 projectId를 대신 돌려준다.
     */
    List<Long> search(String keyword);

    /**
     * 재색인(reindexAllProjects) 전용 — 페이지 단위로 넘겨받은 프로젝트들을 한 번에 색인한다.
     * index()와 달리 이벤트를 발행하지 않고 즉시 동기 처리한다. 실패는 로그만 남기고 삼킨다 —
     * 절대 예외를 던지지 않는다(한 페이지 실패가 전체 재색인을 중단시키면 안 되므로).
     */
    void bulkIndex(List<Project> projects);
}
