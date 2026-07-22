package com.growmighty.lectures.firstday.board.notice.application;

import com.growmighty.lectures.firstday.board.notice.domain.ProjectNotice;
import com.growmighty.lectures.firstday.board.notice.domain.NoticeRepository;
import com.growmighty.lectures.firstday.common.entity.UserRole;
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
    public ProjectNotice register(Long projectId, Long authorId, String title, String content) {
        return noticeRepository.save(ProjectNotice.create(projectId, authorId, title, content));
    }

    @Transactional(readOnly = true)
    public List<ProjectNotice> getByProject(Long projectId) {
        return noticeRepository.findByProjectId(projectId);
    }

    @Transactional
    public ProjectNotice update(Long noticeId, Long requesterId, UserRole requesterRole, String title, String content) {
        ProjectNotice notice = getNotice(noticeId);
        notice.update(requesterId, requesterRole, title, content);
        return notice;
    }

    @Transactional
    public void delete(Long noticeId, Long requesterId, UserRole requesterRole) {
        getNotice(noticeId).delete(requesterId, requesterRole);
    }

    private ProjectNotice getNotice(Long noticeId) {
        return noticeRepository.findById(noticeId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 공지입니다. noticeId=" + noticeId));
    }
}
