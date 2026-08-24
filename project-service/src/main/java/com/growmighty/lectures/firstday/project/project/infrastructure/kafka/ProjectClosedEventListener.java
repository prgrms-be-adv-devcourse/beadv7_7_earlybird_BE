package com.growmighty.lectures.firstday.project.project.infrastructure.kafka;

import com.growmighty.lectures.firstday.project.project.application.port.ProjectStatusChangedEventPublisher;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.project.infrastructure.kafka.dto.ProjectStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * ProjectServiceImpl.closeProjectByDeadline()이 발행한 ProjectClosedEvent를 실제로 Kafka로 내보낸다.
 *
 * <p>AFTER_COMMIT + fallbackExecution: ProjectSearchIndexEventListener와 같은 이유·같은 패턴(그 클래스
 * 주석 참고) — closeProjectByDeadline()은 @Retryable(낙관적 락 충돌 시 재시도)이라, 커밋 전에 발행하면
 * 롤백된 시도까지 settlement에 새어나갈 수 있다. AFTER_COMMIT이면 실제로 커밋에 성공한 시도만 발행되고,
 * 트랜잭션 없는 테스트 컨텍스트에서도 fallbackExecution=true라 즉시 동작한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ProjectClosedEventListener {

    private final ProjectRepository projectRepository;
    private final ProjectStatusChangedEventPublisher publisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onProjectClosed(ProjectClosedEvent event) {
        Project project = projectRepository.findById(event.projectId()).orElse(null);
        if (project == null) {
            log.debug("발행 시점엔 이미 삭제된 프로젝트라 건너뜀. projectId={}", event.projectId());
            return;
        }
        publisher.publish(ProjectStatusChangedEvent.of(
                project.getProjectId(), project.getTitle(), project.getCreatorId(), project.getStatus().name()));
    }
}
