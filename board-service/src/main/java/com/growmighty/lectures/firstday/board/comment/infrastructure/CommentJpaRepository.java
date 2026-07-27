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
    // 정렬은 오름차순(ASC) — notice/review 목록과 달리 댓글은 대화 스레드라 위→아래로 작성 순서대로 읽는 게 자연스럽다.
    // 응용계층이 이 결과를 parentId로 그룹핑해 "루트 다음에 그 답글들"을 조립하므로, 루트/답글 모두 이 순서를 그대로 물려받는다.
    @Query("SELECT c FROM Comment c "
        + "WHERE c.targetType = :targetType AND c.targetId = :targetId AND c.status <> :excludedStatus "
        + "ORDER BY c.createdAt ASC")
    List<Comment> findVisibleByTargetTypeAndTargetId(@Param("targetType") CommentTargetType targetType,
                                                       @Param("targetId") Long targetId,
                                                       @Param("excludedStatus") CommentStatus excludedStatus);
}
