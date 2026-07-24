package com.growmighty.lectures.firstday.board.comment.infrastructure;

import com.growmighty.lectures.firstday.board.comment.domain.Comment;
import com.growmighty.lectures.firstday.board.comment.domain.CommentStatus;
import com.growmighty.lectures.firstday.board.comment.domain.CommentTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentJpaRepository extends JpaRepository<Comment, Long> {
    // targetType/targetId/status 세 조건 + 정렬을 파생 쿼리 이름으로 표현하면 메서드명이 지나치게 길어져 @Query로 명시한다.
    @Query("SELECT c FROM Comment c "
        + "WHERE c.targetType = :targetType AND c.targetId = :targetId AND c.status <> :excludedStatus "
        + "ORDER BY c.createdAt DESC")
    List<Comment> findVisibleByTargetTypeAndTargetId(@Param("targetType") CommentTargetType targetType,
                                                       @Param("targetId") Long targetId,
                                                       @Param("excludedStatus") CommentStatus excludedStatus);
}
