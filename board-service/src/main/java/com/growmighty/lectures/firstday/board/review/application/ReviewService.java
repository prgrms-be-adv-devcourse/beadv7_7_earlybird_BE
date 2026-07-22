package com.growmighty.lectures.firstday.board.review.application;

import com.growmighty.lectures.firstday.board.review.domain.Review;
import com.growmighty.lectures.firstday.board.review.domain.ReviewRepository;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepository;

    @Transactional
    public Review register(Long projectId, Long orderId, Long authorId, BigDecimal rating, String content) {
        // TODO(팀): 리워드 수령 여부 검증 + 프로젝트당 1인 1리뷰 정책 결정
        return reviewRepository.save(Review.create(projectId, orderId, authorId, rating, content));
    }

    @Transactional(readOnly = true)
    public List<Review> getByProject(Long projectId) {
        // TODO(팀): 평점 통계(평균/분포) 응답 추가
        return reviewRepository.findByProjectId(projectId);
    }

    @Transactional
    public void delete(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 리뷰입니다. reviewId=" + reviewId));
        reviewRepository.delete(review);
    }
}
