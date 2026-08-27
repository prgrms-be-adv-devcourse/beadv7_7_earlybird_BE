package com.growmighty.lectures.firstday.project.project.infrastructure.kafka;

/**
 * deleteInternal()이 프로젝트 삭제를 커밋하기 직전에 발행하는 신호 — projectId만 담는다.
 * ProjectFilesDeletionRequestedEventListener가 AFTER_COMMIT 시점에 실제 Kafka 이벤트를
 * file-service로 내보낸다(ProjectClosedEvent와 같은 이유: 커밋 전에 내보내면 롤백된 삭제
 * 시도까지 file-service로 새어나갈 수 있고, 트랜잭션이 카프카 응답을 기다리며 DB 락을
 * 물고 있게 된다).
 */
public record ProjectFilesDeletionRequestedEvent(Long projectId) {
}
