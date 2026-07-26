package com.growmighty.lectures.firstday.board.review.domain;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository {
    Review save(Review review);

    Optional<Review> findById(Long id);

    List<Review> findVisibleByProjectId(Long projectId);

    /** 1인 1리뷰 정책 확인용 — 삭제된 리뷰는 제외한다 (삭제 후 재작성 허용) */
    boolean existsActiveByProjectIdAndAuthorId(Long projectId, Long authorId);

    /** 댓글 대상(targetId) 존재 검증용 — 삭제된 리뷰는 존재하지 않는 것으로 취급 */
    boolean existsVisibleById(Long id);
}
