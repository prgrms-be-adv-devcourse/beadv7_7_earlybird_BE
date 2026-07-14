package com.growmighty.lectures.firstday.board.notice.application;

import com.growmighty.lectures.firstday.board.notice.domain.Notice;
import com.growmighty.lectures.firstday.board.notice.domain.NoticeRepository;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NoticeService {
    private final NoticeRepository noticeRepository;

    // TODO(팀): 공지 등록 시 후원자에게 알림 이벤트 발행 (NoticePublished → notification-service)
    @Transactional
    public Notice register(Long projectId, String title, String content) {
        return noticeRepository.save(Notice.create(projectId, title, content));
    }

    @Transactional(readOnly = true)
    public List<Notice> getByProject(Long projectId) {
        return noticeRepository.findByProjectId(projectId);
    }

    @Transactional
    public Notice update(Long noticeId, String title, String content) {
        Notice notice = getNotice(noticeId);
        notice.update(title, content);
        return notice;
    }

    @Transactional
    public void delete(Long noticeId) {
        noticeRepository.delete(getNotice(noticeId));
    }

    private Notice getNotice(Long noticeId) {
        return noticeRepository.findById(noticeId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 공지입니다. noticeId=" + noticeId));
    }
}
