package com.growmighty.lectures.firstday.project.project.application.port;

import com.growmighty.lectures.firstday.project.project.domain.Project;

import java.util.List;

/**
 * ES 검색 인덱스에 대한 계약. index/remove는 실패해도 예외를 던지지 않는다(호출부의 MySQL
 * 트랜잭션·응답에 영향을 주면 안 되므로 — 구현체가 내부에서 흡수). search만 ES 장애 시
 * ServiceUnavailableException을 던진다(design doc: LIKE 폴백 없음, 명시적 503).
 */
public interface ProjectSearchPort {

    /** 색인 실패는 로그만 남기고 삼킨다 — 절대 예외를 던지지 않는다. */
    void index(Project project);

    /** 삭제 실패는 로그만 남기고 삼킨다 — 절대 예외를 던지지 않는다. */
    void remove(Long projectId);

    /** nori 키워드 매치 ∪ 임베딩 kNN 하이브리드 검색. 매치 없으면 빈 리스트. ES 장애 시 ServiceUnavailableException. */
    List<Long> search(String keyword);
}
