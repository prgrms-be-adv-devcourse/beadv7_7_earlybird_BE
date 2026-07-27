package com.growmighty.lectures.firstday.board.comment.application;

import com.growmighty.lectures.firstday.board.application.port.ProjectPort;
import com.growmighty.lectures.firstday.board.application.port.UserPort;
import com.growmighty.lectures.firstday.board.comment.application.dto.CommentResult;
import com.growmighty.lectures.firstday.board.comment.application.dto.DeleteCommentCommand;
import com.growmighty.lectures.firstday.board.comment.application.dto.RegisterCommentCommand;
import com.growmighty.lectures.firstday.board.comment.application.dto.RegisterReplyCommand;
import com.growmighty.lectures.firstday.board.comment.application.dto.UpdateCommentCommand;
import com.growmighty.lectures.firstday.board.comment.application.exception.ConcurrentUpdateFailedException;
import com.growmighty.lectures.firstday.board.comment.domain.Comment;
import com.growmighty.lectures.firstday.board.comment.domain.CommentRepository;
import com.growmighty.lectures.firstday.board.comment.domain.CommentTargetType;
import com.growmighty.lectures.firstday.board.notice.domain.ProjectNoticeRepository;
import com.growmighty.lectures.firstday.board.review.domain.ReviewRepository;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository commentRepository;
    private final ProjectNoticeRepository projectNoticeRepository;
    private final ReviewRepository reviewRepository;
    private final UserPort userPort;
    private final ProjectPort projectPort;

    @Transactional
    public CommentResult register(RegisterCommentCommand command) {
        validateTargetExists(command.targetType(), command.targetId());
        String authorName = userPort.getUser(command.authorId()).name();
        Comment comment = commentRepository.save(
            Comment.create(command.targetType(), command.targetId(), command.authorId(), authorName, command.content()));
        return CommentResult.from(comment);
    }

    @Transactional
    public CommentResult registerReply(RegisterReplyCommand command) {
        Comment parent = findComment(command.parentId());
        String authorName = userPort.getUser(command.authorId()).name();
        Comment reply = commentRepository.save(Comment.reply(parent, command.authorId(), authorName, command.content()));
        return CommentResult.from(reply);
    }

    // 대댓글이 구조적으로 1단까지만 허용되므로(Comment.reply()가 강제), 그룹핑도 한 번만 하면 된다 —
    // 재귀적으로 트리를 조립할 필요가 없다.
    @Transactional(readOnly = true)
    public List<CommentResult> getByTarget(CommentTargetType targetType, Long targetId) {
        List<Comment> all = commentRepository.findVisibleByTargetTypeAndTargetId(targetType, targetId);
        Map<Long, List<Comment>> repliesByParentId = all.stream()
            .filter(comment -> comment.getParentId() != null)
            .collect(Collectors.groupingBy(Comment::getParentId));

        return all.stream()
            .filter(comment -> comment.getParentId() == null)
            .map(root -> CommentResult.from(root, repliesByParentId.getOrDefault(root.getId(), List.of())))
            .toList();
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public CommentResult update(UpdateCommentCommand command) {
        Comment comment = findComment(command.commentId());
        comment.update(command.requesterId(), command.content());
        return CommentResult.from(comment);
    }

    @Recover
    public CommentResult recoverUpdateConflict(ObjectOptimisticLockingFailureException e, UpdateCommentCommand command) {
        throw new ConcurrentUpdateFailedException(
            "의견 수정 중 동시 수정 충돌이 반복되어 실패했습니다. commentId=" + command.commentId());
    }

    @Recover
    public CommentResult recoverUpdateOther(RuntimeException e, UpdateCommentCommand command) {
        throw e;
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public void delete(DeleteCommentCommand command) {
        findComment(command.commentId()).delete(command.requesterId(), command.requesterRole());
    }

    @Recover
    public void recoverDeleteConflict(ObjectOptimisticLockingFailureException e, DeleteCommentCommand command) {
        throw new ConcurrentUpdateFailedException(
            "의견 삭제 중 동시 수정 충돌이 반복되어 실패했습니다. commentId=" + command.commentId());
    }

    @Recover
    public void recoverDeleteOther(RuntimeException e, DeleteCommentCommand command) {
        throw e;
    }

    private void validateTargetExists(CommentTargetType targetType, Long targetId) {
        boolean exists = switch (targetType) {
            case PROJECT -> projectPort.existsProject(targetId);
            case PROJECT_NOTICE -> projectNoticeRepository.existsVisibleById(targetId);
            case REVIEW -> reviewRepository.existsVisibleById(targetId);
        };
        if (!exists) {
            throw new EntityNotFoundException(
                "존재하지 않는 댓글 대상입니다. targetType=" + targetType + ", targetId=" + targetId);
        }
    }

    private Comment findComment(Long commentId) {
        return commentRepository.findById(commentId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 의견입니다. commentId=" + commentId));
    }
}