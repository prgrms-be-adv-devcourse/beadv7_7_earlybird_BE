package com.growmighty.lectures.firstday.board.review.application;

import com.growmighty.lectures.firstday.board.feign.port.OrderPort;
import com.growmighty.lectures.firstday.board.feign.port.UserPort;
import com.growmighty.lectures.firstday.board.feign.port.dto.PurchaseVerification;
import com.growmighty.lectures.firstday.board.feign.port.dto.UserSnapshot;
import com.growmighty.lectures.firstday.board.review.application.dto.RegisterReviewCommand;
import com.growmighty.lectures.firstday.board.review.application.dto.ReviewResult;
import com.growmighty.lectures.firstday.board.review.application.exception.DuplicateReviewException;
import com.growmighty.lectures.firstday.board.review.application.exception.PurchaseNotVerifiedException;
import com.growmighty.lectures.firstday.board.review.domain.Review;
import com.growmighty.lectures.firstday.board.review.domain.ReviewRepository;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private UserPort userPort;
    @Mock
    private OrderPort orderPort;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewRepository, userPort, orderPort);
    }

    @Test
    @DisplayName("검증을 통과하면 authorName/rewardName을 채워 등록한다")
    void register_success() {
        when(reviewRepository.existsActiveByProjectIdAndAuthorId(10L, 1L)).thenReturn(false);
        when(userPort.getUser(1L)).thenReturn(new UserSnapshot(1L, "홍길동"));
        when(orderPort.verifyPurchase(1L, 100L)).thenReturn(new PurchaseVerification(true, "얼리버드 리워드"));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResult result = reviewService.register(
                new RegisterReviewCommand(10L, 100L, 1L, BigDecimal.valueOf(4.5), "좋아요"));

        assertThat(result.authorName()).isEqualTo("홍길동");
        assertThat(result.rewardName()).isEqualTo("얼리버드 리워드");
    }

    @Test
    @DisplayName("같은 프로젝트에 이미 활성 리뷰가 있으면 외부 연동 없이 바로 실패한다")
    void register_duplicateReview_failsBeforeExternalCalls() {
        when(reviewRepository.existsActiveByProjectIdAndAuthorId(10L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.register(
                new RegisterReviewCommand(10L, 100L, 1L, BigDecimal.valueOf(4.5), "좋아요")))
            .isInstanceOf(DuplicateReviewException.class);

        verifyNoInteractions(userPort, orderPort);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("구매가 확인되지 않으면 등록이 거부되고 저장하지 않는다")
    void register_purchaseNotVerified_throws() {
        when(reviewRepository.existsActiveByProjectIdAndAuthorId(10L, 1L)).thenReturn(false);
        when(userPort.getUser(1L)).thenReturn(new UserSnapshot(1L, "홍길동"));
        when(orderPort.verifyPurchase(1L, 100L)).thenReturn(new PurchaseVerification(false, null));

        assertThatThrownBy(() -> reviewService.register(
                new RegisterReviewCommand(10L, 100L, 1L, BigDecimal.valueOf(4.5), "좋아요")))
            .isInstanceOf(PurchaseNotVerifiedException.class);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("user-service 조회에 실패하면 구매 검증 없이 바로 실패한다")
    void register_userInfoUnavailable_failsBeforeOrderCheck() {
        when(reviewRepository.existsActiveByProjectIdAndAuthorId(10L, 1L)).thenReturn(false);
        when(userPort.getUser(1L)).thenThrow(new ServiceUnavailableException("사용자 정보를 확인할 수 없습니다."));

        assertThatThrownBy(() -> reviewService.register(
                new RegisterReviewCommand(10L, 100L, 1L, BigDecimal.valueOf(4.5), "좋아요")))
            .isInstanceOf(ServiceUnavailableException.class);

        verifyNoInteractions(orderPort);
        verify(reviewRepository, never()).save(any());
    }
}