package com.growmighty.lectures.firstday.board.notice.infrastructure;

import com.growmighty.lectures.firstday.board.notice.domain.Notice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticeJpaRepository extends JpaRepository<Notice, Long> {
    List<Notice> findByProjectId(Long projectId);
}
