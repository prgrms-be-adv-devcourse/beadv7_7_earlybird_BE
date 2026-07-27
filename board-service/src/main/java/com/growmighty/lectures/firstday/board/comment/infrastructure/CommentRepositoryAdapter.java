package com.growmighty.lectures.firstday.board.comment.infrastructure;

import com.growmighty.lectures.firstday.board.comment.domain.Comment;
import com.growmighty.lectures.firstday.board.comment.domain.CommentRepository;
import com.growmighty.lectures.firstday.board.comment.domain.CommentStatus;
import com.growmighty.lectures.firstday.board.comment.domain.CommentTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CommentRepositoryAdapter implements CommentRepository {
    private final CommentJpaRepository jpaRepository;

    @Override
    public Comment save(Comment comment) {
        return jpaRepository.save(comment);
    }

    @Override
    public Optional<Comment> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Comment> findVisibleByTargetTypeAndTargetId(CommentTargetType targetType, Long targetId) {
        return jpaRepository.findVisibleByTargetTypeAndTargetId(targetType, targetId, CommentStatus.DELETED);
    }
}
