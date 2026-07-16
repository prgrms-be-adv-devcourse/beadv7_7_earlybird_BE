package com.growmighty.lectures.firstday.project.infrastructure.search;

import com.growmighty.lectures.firstday.project.domain.event.ProjectChangedEvent;
import com.growmighty.lectures.firstday.project.domain.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectIndexer {

    private final ProjectRepository projectRepository;
    private final ProjectSearchRepository searchRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)      // ★ 커밋 성공 후에만 실행
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true) // 커밋된 tx는 끝났으므로 새 tx로 조회
    public void handle(ProjectChangedEvent event) {
        try {
            projectRepository.findById(event.projectId())
                .ifPresent(project -> searchRepository.save(ProjectDocument.from(project)));
            log.info("색인 완료 projectId={}", event.projectId());
        } catch (Exception e) {
            // ★ 예외를 삼킨다 = "검색 색인 실패가 본 업무(등록/주문)를 죽이지 않는다"
            //   대가: 이벤트 유실 가능 → Step 6에서 일부러 재현하고, 재색인(3-4)으로 보정한다
            log.error("검색 색인 실패 projectId={} — 재색인 배치로 보정 필요", event.projectId(), e);
        }
    }
}
