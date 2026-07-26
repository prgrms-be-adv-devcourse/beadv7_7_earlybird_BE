package com.growmighty.lectures.firstday.board.notice.domain;

import java.util.List;
import java.util.Optional;

public interface ProjectNoticeRepository {
    ProjectNotice save(ProjectNotice notice);

    Optional<ProjectNotice> findById(Long id);

    List<ProjectNotice> findVisibleByProjectId(Long projectId);


}
