package com.growmighty.lectures.firstday.board.comment.infrastructure;

import com.growmighty.lectures.firstday.board.comment.domain.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentJpaRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByProjectId(Long projectId);
}
