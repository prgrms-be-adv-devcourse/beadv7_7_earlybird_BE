package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * ProjectSearchAdapter.index()/remove()가 발행한 이벤트를 실제로 처리한다.
 *
 * <p>AFTER_COMMIT: 호출부(ProjectServiceImpl의 create/update/delete)의 MySQL 트랜잭션이 커밋된
 * 뒤에만 실행되고, 롤백되면 아예 실행되지 않는다.
 *
 * <p>색인 요청은 이벤트가 실어온 값이 아니라 여기서 ProjectRepository로 "지금" 다시 조회한 값을
 * 색인한다. 조회 시점에 이미 삭제된 프로젝트라면 색인하지 않고 건너뛴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ProjectSearchIndexEventListener {

    private final ProjectSearchAdapter adapter;
    private final ProjectRepository projectRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onIndexRequested(ProjectIndexRequestedEvent event) {
        Project project = projectRepository.findById(event.projectId()).orElse(null);
        if (project == null) {
            log.debug("색인 요청을 처리하는 시점엔 이미 삭제된 프로젝트라 건너뜀. projectId={}", event.projectId());
            return;
        }
        adapter.applyIndex(project);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onRemoveRequested(ProjectRemovedFromIndexEvent event) {
        adapter.applyRemove(event.projectId());
    }
}
