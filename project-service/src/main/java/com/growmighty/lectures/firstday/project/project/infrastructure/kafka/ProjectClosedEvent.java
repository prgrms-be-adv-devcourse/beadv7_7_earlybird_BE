package com.growmighty.lectures.firstday.project.project.infrastructure.kafka;

/**
 * 마감 배치(ProjectServiceImpl.closeProjectByDeadline)가 프로젝트를 성공/실패로 확정했다는 신호 —
 * projectId만 담는다. ProjectClosedEventListener가 AFTER_COMMIT 시점에 DB에서 다시 조회해 그 순간의
 * 최신 status/creatorId로 settlement-service에 보낼 이벤트를 만든다(ProjectSearchIndexEventListener와
 * 같은 이유: 이벤트에 내용을 실어 보내면 재시도로 처리 순서가 뒤섞일 때 오래된 값을 보낼 위험이 있다).
 */
public record ProjectClosedEvent(Long projectId) {
}
