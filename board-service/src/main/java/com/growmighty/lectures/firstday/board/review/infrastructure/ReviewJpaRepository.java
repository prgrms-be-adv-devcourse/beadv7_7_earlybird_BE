package com.growmighty.lectures.firstday.board.review.infrastructure;

import com.growmighty.lectures.firstday.board.review.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewJpaRepository extends JpaRepository<Review, Long> {
    List<Review> findByProjectId(Long projectId);
}
