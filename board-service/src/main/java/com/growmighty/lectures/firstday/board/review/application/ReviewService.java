package com.growmighty.lectures.firstday.board.review.application;

import com.growmighty.lectures.firstday.board.event.ReviewCreatedEvent;
import com.growmighty.lectures.firstday.board.event.port.DomainEventPublisher;
import com.growmighty.lectures.firstday.board.feign.port.FilePort;
import com.growmighty.lectures.firstday.board.feign.port.OrderPort;
import com.growmighty.lectures.firstday.board.feign.port.UserPort;
import com.growmighty.lectures.firstday.board.feign.port.dto.PurchaseVerification;
import com.growmighty.lectures.firstday.board.review.application.dto.DeleteReviewCommand;
import com.growmighty.lectures.firstday.board.review.application.dto.RegisterReviewCommand;
import com.growmighty.lectures.firstday.board.review.application.dto.ReviewResult;
import com.growmighty.lectures.firstday.board.review.application.dto.UpdateReviewCommand;
import com.growmighty.lectures.firstday.board.review.application.exception.ConcurrentUpdateFailedException;
import com.growmighty.lectures.firstday.board.review.application.exception.DuplicateReviewException;
import com.growmighty.lectures.firstday.board.review.application.exception.PurchaseNotVerifiedException;
import com.growmighty.lectures.firstday.board.review.domain.Review;
import com.growmighty.lectures.firstday.board.review.domain.ReviewRepository;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final UserPort userPort;
    private final OrderPort orderPort;
    private final DomainEventPublisher domainEventPublisher;
    private final FilePort filePort;

    @Transactional
    public ReviewResult register(RegisterReviewCommand command) {
        if (reviewRepository.existsActiveByProjectIdAndAuthorId(command.projectId(), command.authorId())) {
            throw new DuplicateReviewException(
                "이미 이 프로젝트에 리뷰를 작성했습니다. projectId=" + command.projectId() + ", authorId=" + command.authorId());
        }

        String authorName = userPort.getUser(command.authorId()).name();

        PurchaseVerification verification = orderPort.verifyPurchase(command.authorId(), command.rewardId());
        if (!verification.verified()) {
            throw new PurchaseNotVerifiedException(
                "구매가 확인되지 않아 리뷰를 작성할 수 없습니다. rewardId=" + command.rewardId());
        }

        Review review = reviewRepository.save(Review.create(command.projectId(), command.rewardId(), verification.rewardName(),
            command.authorId(), authorName, command.rating(), command.content()));

        domainEventPublisher.publish(
            ReviewCreatedEvent.of(review.getId(), review.getProjectId(), review.getAuthorId(), review.getAuthorName()));

        return ReviewResult.from(review);
    }

    @Transactional(readOnly = true)
    public List<ReviewResult> getByProject(Long projectId) {
        // TODO(팀): 평점 통계(평균/분포) 응답 추가
        List<Review> reviews = reviewRepository.findVisibleByProjectId(projectId);
        Map<Long, List<String>> photoUrlsByReviewId =
            filePort.getReviewPhotoUrls(reviews.stream().map(Review::getId).toList());
        return reviews.stream()
            .map(review -> ReviewResult.from(review, photoUrlsByReviewId.getOrDefault(review.getId(),  List.of())))
            .toList();
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public ReviewResult update(UpdateReviewCommand command) {
        Review review = findReview(command.reviewId());
        review.update(command.requesterId(), command.rating(), command.content());
        return ReviewResult.from(review);
    }

    @Recover
    public ReviewResult recoverUpdateConflict(ObjectOptimisticLockingFailureException e, UpdateReviewCommand command) {
        throw new ConcurrentUpdateFailedException(
            "리뷰 수정 중 동시 수정 충돌이 반복되어 실패했습니다. reviewId=" + command.reviewId());
    }

    @Recover
    public ReviewResult recoverUpdateOther(RuntimeException e, UpdateReviewCommand command) {
        throw e;
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public void delete(DeleteReviewCommand command) {
        findReview(command.reviewId()).delete(command.requesterId(), command.requesterRole());
    }

    @Recover
    public void recoverDeleteConflict(ObjectOptimisticLockingFailureException e, DeleteReviewCommand command) {
        throw new ConcurrentUpdateFailedException(
            "리뷰 삭제 중 동시 수정 충돌이 반복되어 실패했습니다. reviewId=" + command.reviewId());
    }

    @Recover
    public void recoverDeleteOther(RuntimeException e, DeleteReviewCommand command) {
        throw e;
    }

    private Review findReview(Long reviewId) {
        return reviewRepository.findById(reviewId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 리뷰입니다. reviewId=" + reviewId));
    }
}
