package com.growmighty.lectures.firstday.board.notice.infrastructure;

import com.growmighty.lectures.firstday.board.notice.domain.ProjectNotice;
import com.growmighty.lectures.firstday.board.notice.domain.ProjectNoticeStatus;
import com.growmighty.lectures.firstday.board.notice.domain.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NoticeRepositoryAdapter implements NoticeRepository {
    private final NoticeJpaRepository jpaRepository;

    @Override
    public ProjectNotice save(ProjectNotice notice) {
        return jpaRepository.save(notice);
    }

    @Override
    public Optional<ProjectNotice> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<ProjectNotice> findVisibleByProjectId(Long projectId) {
        return jpaRepository.findByProjectIdAndStatusNotOrderByCreatedAtDesc(projectId, ProjectNoticeStatus.DELETED);
    }
}
