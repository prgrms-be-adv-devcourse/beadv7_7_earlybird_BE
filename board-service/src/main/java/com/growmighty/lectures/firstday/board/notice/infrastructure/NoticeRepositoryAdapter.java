package com.growmighty.lectures.firstday.board.notice.infrastructure;

import com.growmighty.lectures.firstday.board.notice.domain.Notice;
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
    public Notice save(Notice notice) {
        return jpaRepository.save(notice);
    }

    @Override
    public Optional<Notice> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Notice> findByProjectId(Long projectId) {
        return jpaRepository.findByProjectId(projectId);
    }

    @Override
    public void delete(Notice notice) {
        jpaRepository.delete(notice);
    }
}
