package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ProjectSearchIndexEventListener가 (느린 외부 호출인) 임베딩 생성을 끝낸 뒤, 그 결과값만 들고
 * 호출하는 전용 트랜잭션 경계. projectId로 다시 조회한 managed 엔티티를 직접 수정해 dirty-checking으로
 * 저장한다 — detached 엔티티를 매번 save()로 merge하면, 그 사이 다른 트랜잭션이 바꾼 다른 필드를
 * 이 메서드가 들고 있던 오래된 스냅샷으로 덮어쓸 위험이 있다(@Version이 있어 조용히 덮어써지진
 * 않고 낙관적 락 예외로 시끄럽게 실패하지만, 그 자체도 불필요한 실패다). 이 메서드를 짧게 유지해야
 * OpenAI/ES 같은 외부 호출이 DB 트랜잭션(커넥션)을 물고 있는 일이 없다.
 */
@Component
@RequiredArgsConstructor
class ProjectEmbeddingPersister {

    private final ProjectRepository projectRepository;

    /** 그 사이 프로젝트가 삭제됐으면 null을 반환한다 — 호출부가 색인을 건너뛰도록. */
    @Transactional
    Project updateEmbedding(Long projectId, float[] embedding) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return null;
        }
        project.updateEmbedding(embedding);
        return project;
    }
}
