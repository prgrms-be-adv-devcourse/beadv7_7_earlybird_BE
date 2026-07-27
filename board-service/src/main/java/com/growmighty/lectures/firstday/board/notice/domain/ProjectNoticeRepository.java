package com.growmighty.lectures.firstday.board.notice.domain;

import java.util.List;
import java.util.Optional;

public interface ProjectNoticeRepository {
    ProjectNotice save(ProjectNotice notice);

    Optional<ProjectNotice> findById(Long id);

    List<ProjectNotice> findVisibleByProjectId(Long projectId);

    /** 댓글 대상(targetId) 존재 검증용 — 삭제된 공지는 존재하지 않는 것으로 취급 */
    boolean existsVisibleById(Long id);
}
