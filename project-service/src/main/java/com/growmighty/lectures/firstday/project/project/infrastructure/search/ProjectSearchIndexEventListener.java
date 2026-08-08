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
 * 뒤에만 실행되고, 롤백되면 아예 실행되지 않는다 — board-service ReviewCreatedNotificationListener와
 * 같은 이유·같은 패턴(그 클래스 주석 참고).
 *
 * <p>fallbackExecution = true: 위 프로덕션 경로는 항상 트랜잭션 안에서 발행되지만, 테스트 코드
 * (예: ProjectSearchAdapterIntegrationTest, ProjectServiceImplSearchIntegrationTest)는 트랜잭션
 * 없이 adapter.index()/remove()를 직접 호출한다 — 이 옵션이 없으면 그런 논-트랜잭션 컨텍스트에서
 * 발행된 이벤트는 @TransactionalEventListener 기본 동작상 조용히 버려진다. true로 두면 트랜잭션이
 * 없을 때는 즉시(동기) 실행되어, 트랜잭션 유무와 관계없이 index()/remove()가 항상 동작한다.
 *
 * <p>색인 요청은 이벤트가 실어온 값이 아니라 여기서 ProjectRepository로 "지금" 다시 조회한 값을
 * 색인한다 — 이벤트가 커밋 전 스냅샷을 그대로 실어 보내면, 같은 프로젝트에 대한 여러 이벤트가
 * 발행 순서와 다르게 처리될 때(재시도, 서킷브레이커 대기 등으로 처리 시점이 뒤섞일 수 있음) 더
 * 오래된 이벤트가 나중에 처리되면서 최신 내용을 옛 내용으로 덮어써버릴 수 있다. 매번 다시 조회하면
 * 몇 번을, 어떤 순서로 처리하든 결과가 항상 그 순간의 DB 최신 상태로 수렴한다(멱등성). 조회 시점에
 * 이미 삭제된 프로젝트라면(다른 이벤트가 먼저 처리됨) 색인하지 않고 건너뛴다 — 삭제된 프로젝트를
 * 검색 결과에 되살리면 안 되므로.
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
        // 실제 AI 임베딩 연동 전까지는 null 상태를 유지 (가짜 난수 벡터 오염 방지)
        adapter.applyIndex(project);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onRemoveRequested(ProjectRemovedFromIndexEvent event) {
        adapter.applyRemove(event.projectId());
    }
}
