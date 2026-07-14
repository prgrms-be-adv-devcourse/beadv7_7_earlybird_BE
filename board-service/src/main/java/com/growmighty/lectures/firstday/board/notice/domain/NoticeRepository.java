package com.growmighty.lectures.firstday.board.notice.domain;

import java.util.List;
import java.util.Optional;

public interface NoticeRepository {
    Notice save(Notice notice);

    Optional<Notice> findById(Long id);

    List<Notice> findByProjectId(Long projectId);

    void delete(Notice notice);
}
