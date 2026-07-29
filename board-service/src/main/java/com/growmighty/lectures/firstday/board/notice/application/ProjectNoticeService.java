package com.growmighty.lectures.firstday.board.notice.application;

import com.growmighty.lectures.firstday.board.feign.port.UserPort;
import com.growmighty.lectures.firstday.board.notice.application.dto.DeleteProjectNoticeCommand;
import com.growmighty.lectures.firstday.board.notice.application.dto.ProjectNoticeResult;
import com.growmighty.lectures.firstday.board.notice.application.dto.RegisterProjectNoticeCommand;
import com.growmighty.lectures.firstday.board.notice.application.dto.UpdateProjectNoticeCommand;
import com.growmighty.lectures.firstday.board.notice.application.exception.ConcurrentUpdateFailedException;
import com.growmighty.lectures.firstday.board.notice.domain.ProjectNotice;
import com.growmighty.lectures.firstday.board.notice.domain.ProjectNoticeRepository;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectNoticeService {
    private final ProjectNoticeRepository noticeRepository;
    private final UserPort userPort;

    // TODO(팀): 공지 등록 시 후원자에게 알림 이벤트 발행 (NoticePublished → notification-service)
    @Transactional
    public ProjectNoticeResult register(RegisterProjectNoticeCommand command) {
        String authorName = userPort.getUser(command.authorId()).name();
        ProjectNotice notice = noticeRepository.save(
            ProjectNotice.create(command.projectId(), command.authorId(), authorName, command.title(), command.content()));
        return ProjectNoticeResult.from(notice);
    }

    @Transactional(readOnly = true)
    public List<ProjectNoticeResult> getByProject(Long projectId) {
        return noticeRepository.findVisibleByProjectId(projectId).stream().map(ProjectNoticeResult::from).toList();
    }

    // readOnly=true는 Hibernate가 flush를 건너뛰게 해 조회수 증가가 커밋되지 않으므로 쓰지 않는다.
    @Transactional
    public ProjectNoticeResult getNotice(Long noticeId) {
        ProjectNotice notice = findNotice(noticeId);
        notice.increaseViewCount();
        return ProjectNoticeResult.from(notice);
    }

    /**
     * 동시에 여러 요청이 같은 공지의 제목/내용을 고치거나 지우려 할 때 버전 충돌이 날 수 있어 재시도한다.
     * viewCount는 낙관적 락 검사에서 제외돼 있어(ProjectNotice 참고) getNotice의 조회수 증가와는 경합하지 않는다.
     * 재시도 어드바이저가 트랜잭션을 감싸도록 순서가 보장돼야 한다(BoardServiceApplication의 EnableRetry order 설정).
     * 그래야 낙관적 락 충돌로 커밋이 실패했을 때 매 재시도가 새 트랜잭션에서 엔티티를 다시 읽어온다.
     */
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public ProjectNoticeResult update(UpdateProjectNoticeCommand command) {
        ProjectNotice notice = findNotice(command.noticeId());
        notice.update(command.requesterId(), command.requesterRole(), command.title(), command.content());
        return ProjectNoticeResult.from(notice);
    }

    @Recover
    public ProjectNoticeResult recoverUpdateConflict(ObjectOptimisticLockingFailureException e, UpdateProjectNoticeCommand command) {
        throw new ConcurrentUpdateFailedException(
            "공지 수정 중 동시 수정 충돌이 반복되어 실패했습니다. noticeId=" + command.noticeId());
    }

    @Recover
    public ProjectNoticeResult recoverUpdateOther(RuntimeException e, UpdateProjectNoticeCommand command) {
        throw e;
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public void delete(DeleteProjectNoticeCommand command) {
        findNotice(command.noticeId()).delete(command.requesterId(), command.requesterRole());
    }

    @Recover
    public void recoverDeleteConflict(ObjectOptimisticLockingFailureException e, DeleteProjectNoticeCommand command) {
        throw new ConcurrentUpdateFailedException(
            "공지 삭제 중 동시 수정 충돌이 반복되어 실패했습니다. noticeId=" + command.noticeId());
    }

    @Recover
    public void recoverDeleteOther(RuntimeException e, DeleteProjectNoticeCommand command) {
        throw e;
    }

    private ProjectNotice findNotice(Long noticeId) {
        return noticeRepository.findById(noticeId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 공지입니다. noticeId=" + noticeId));
    }
}
