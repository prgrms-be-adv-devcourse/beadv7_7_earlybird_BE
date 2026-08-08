package com.growmighty.lectures.firstday.project.project.infrastructure.search;

/**
 * "이 프로젝트를 색인(또는 재색인)해야 한다"는 이벤트 — projectId만 담는다.
 *
 * <p>title/summary/description 같은 내용을 이벤트에 실어 보내지 않는 이유: 이벤트는 발행 시점
 * (트랜잭션 커밋 "전")의 스냅샷이지만 실제 색인은 그보다 나중(AFTER_COMMIT, 심지어 재시도/지연으로
 * 더 늦어질 수도 있음)에 일어난다. 같은 프로젝트에 대한 update()가 연달아 발생하면 두 이벤트가
 * 발행 순서와 다르게 처리될 수 있는데, 이벤트에 내용을 실어 보내면 "늦게 처리된 오래된 이벤트"가
 * ES에 최신 내용을 덮어써버리는 문제가 생긴다. projectId만 담고 실제 처리 시점(ProjectSearchAdapter
 * .applyIndex)에 DB에서 다시 조회해 그 순간의 최신 값을 색인하면, 몇 번이든 어떤 순서로 처리되든
 * 결과가 항상 DB의 최신 상태로 수렴한다(멱등성) — ProjectRemovedFromIndexEvent도 같은 이유로
 * projectId만 담는다.
 */
record ProjectIndexRequestedEvent(Long projectId) {
}
