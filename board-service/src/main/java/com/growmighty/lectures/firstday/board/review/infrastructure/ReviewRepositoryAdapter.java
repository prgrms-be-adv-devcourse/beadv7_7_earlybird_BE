package com.growmighty.lectures.firstday.board.review.infrastructure;

import com.growmighty.lectures.firstday.board.review.domain.Review;
import com.growmighty.lectures.firstday.board.review.domain.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReviewRepositoryAdapter implements ReviewRepository {
    private final ReviewJpaRepository jpaRepository;

    @Override
    public Review save(Review review) {
        return jpaRepository.save(review);
    }

    @Override
    public Optional<Review> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Review> findByProjectId(Long projectId) {
        return jpaRepository.findByProjectId(projectId);
    }

    @Override
    public void delete(Review review) {
        jpaRepository.delete(review);
    }
}
