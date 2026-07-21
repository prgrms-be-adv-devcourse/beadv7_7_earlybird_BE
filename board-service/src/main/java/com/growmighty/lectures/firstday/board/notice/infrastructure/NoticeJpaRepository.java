package com.growmighty.lectures.firstday.board.notice.infrastructure;

import com.growmighty.lectures.firstday.board.notice.domain.ProjectNotice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticeJpaRepository extends JpaRepository<ProjectNotice, Long> {
    List<ProjectNotice> findByProjectId(Long projectId);
}
