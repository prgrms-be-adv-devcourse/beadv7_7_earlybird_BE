package com.growmighty.lectures.firstday.project.project.infrastructure.kafka;

import com.growmighty.lectures.firstday.project.project.application.port.FilePort;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * ProjectServiceImpl.deleteInternal()이 발행한 ProjectFilesDeletionRequestedEvent를 실제로
 * file-service로 내보낸다. AFTER_COMMIT + fallbackExecution: ProjectClosedEventListener와
 * 같은 이유 — 삭제 트랜잭션이 실제로 커밋된 뒤에만 발행해야 하고, 트랜잭션 없는 테스트
 * 컨텍스트에서도 fallbackExecution=true라 즉시 동작한다.
 */
@Component
@RequiredArgsConstructor
class ProjectFilesDeletionRequestedEventListener {

    private final FilePort filePort;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onProjectFilesDeletionRequested(ProjectFilesDeletionRequestedEvent event) {
        filePort.deleteProjectFiles(event.projectId());
    }
}
