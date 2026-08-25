package com.growmighty.lectures.firstday.project.project.infrastructure.kafka;

import com.growmighty.lectures.firstday.common.kafka.KafkaTopics;
import com.growmighty.lectures.firstday.project.project.application.port.FilePort;
import com.growmighty.lectures.firstday.project.project.infrastructure.kafka.dto.ProjectDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 프로젝트 삭제 시 카프카로 ProjectDeletedEvent를 발행하여 file-service 등에 파일 정리를 위임한다.
 * ProjectFilesDeletionRequestedEventListener가 AFTER_COMMIT 시점에만 호출하므로, 여기서
 * 블로킹 전송을 하더라도 삭제 트랜잭션의 DB 커넥션/락을 물고 있지 않는다.
 * 실패 시에도 본 트랜잭션(프로젝트 삭제)을 막지 않고 WARN 로그만 남기는 best-effort 정책을 유지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaFileEventPublisher implements FilePort {

    private static final long SEND_TIMEOUT_SECONDS = 5L;

    private final KafkaTemplate<String, ProjectDeletedEvent> kafkaTemplate;

    @Override
    public void deleteProjectFiles(Long projectId) {
        ProjectDeletedEvent event = ProjectDeletedEvent.of(projectId);
        try {
            kafkaTemplate.send(
                    KafkaTopics.PROJECT_DELETED,
                    String.valueOf(projectId),
                    event
            ).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("프로젝트 삭제 Kafka 이벤트 발행 성공. projectId={}", projectId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("프로젝트 삭제 Kafka 이벤트 발행 인터럽트 (best-effort). projectId={}, 원인={}", projectId, e.toString());
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            log.warn("프로젝트 삭제 Kafka 이벤트 발행 실패 (best-effort) → 프로젝트 삭제는 계속 진행. projectId={}, 원인={}", projectId, e.toString());
        }
    }
}
