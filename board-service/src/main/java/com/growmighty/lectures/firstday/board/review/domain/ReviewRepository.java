package com.growmighty.lectures.firstday.board.review.domain;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository {
    Review save(Review review);

    Optional<Review> findById(Long id);

    List<Review> findByProjectId(Long projectId);

    void delete(Review review);
}
