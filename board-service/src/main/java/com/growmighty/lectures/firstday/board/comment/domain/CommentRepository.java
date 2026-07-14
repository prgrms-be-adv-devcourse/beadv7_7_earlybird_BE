package com.growmighty.lectures.firstday.board.comment.domain;

import java.util.List;
import java.util.Optional;

public interface CommentRepository {
    Comment save(Comment comment);

    Optional<Comment> findById(Long id);

    List<Comment> findByProjectId(Long projectId);

    void delete(Comment comment);
}
