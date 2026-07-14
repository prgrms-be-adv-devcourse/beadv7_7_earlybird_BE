package com.growmighty.lectures.firstday.board.comment.application;

import com.growmighty.lectures.firstday.board.comment.domain.Comment;
import com.growmighty.lectures.firstday.board.comment.domain.CommentRepository;
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
    public Comment register(Long projectId, Long userId, Long parentId, String content) {
        // TODO(팀): parentId 유효성(같은 프로젝트의 댓글인지) 검증
        return commentRepository.save(Comment.create(projectId, userId, parentId, content));
    }

    @Transactional(readOnly = true)
    public List<Comment> getByProject(Long projectId) {
        return commentRepository.findByProjectId(projectId);
    }

    @Transactional
    public void delete(Long commentId) {
        // TODO(팀): 본인(또는 관리자)만 삭제 가능 — 인증 도입 후 검증
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 의견입니다. commentId=" + commentId));
        commentRepository.delete(comment);
    }
}
