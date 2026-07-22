package com.growmighty.lectures.firstday.board.comment.application;

import com.growmighty.lectures.firstday.board.comment.domain.Comment;
import com.growmighty.lectures.firstday.board.comment.domain.CommentRepository;
import com.growmighty.lectures.firstday.board.comment.domain.CommentTargetType;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository commentRepository;

    @Transactional
    public Comment register(Long projectId, Long userId, String content) {
        return commentRepository.save(Comment.create(CommentTargetType.PROJECT, projectId, userId, content));
    }

    @Transactional
    public Comment registerReply(Long parentId, Long userId, String content) {
        Comment parent = commentRepository.findById(parentId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 의견입니다. commentId=" + parentId));
        return commentRepository.save(Comment.reply(parent, userId, content));
    }

    @Transactional(readOnly = true)
    public List<Comment> getByProject(Long projectId) {
        return commentRepository.findByTargetTypeAndTargetId(CommentTargetType.PROJECT, projectId);
    }

    @Transactional
    public void delete(Long commentId) {
        // TODO(팀): 본인(또는 관리자)만 삭제 가능 — 인증 도입 후 검증
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 의견입니다. commentId=" + commentId));
        commentRepository.delete(comment);
    }
}
