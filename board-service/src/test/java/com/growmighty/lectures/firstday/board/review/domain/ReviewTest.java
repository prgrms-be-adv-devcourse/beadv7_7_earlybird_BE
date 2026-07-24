package com.growmighty.lectures.firstday.board.review.domain;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long ORDER_ID = 1L;
    private static final Long AUTHOR_ID = 1L;
    private static final Long OTHER_AUTHOR_ID = 2L;
    private static final Long ADMIN_ID = 99L;
    private static final String AUTHOR_NAME = "작성자";
    private static final BigDecimal RATING = new BigDecimal("4.5");
    private static final String CONTENT = "리워드가 만족스러웠어요";

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("정상 값으로 생성하면 필드가 채워지고 ACTIVE 상태로 시작한다")
        void create_success() {
            Review review = Review.create(PROJECT_ID, ORDER_ID, AUTHOR_ID, AUTHOR_NAME, RATING, CONTENT);

            assertThat(review.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(review.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(review.getAuthorId()).isEqualTo(AUTHOR_ID);
            assertThat(review.getAuthorName()).isEqualTo(AUTHOR_NAME);
            assertThat(review.getRating().getValue()).isEqualByComparingTo(RATING);
            assertThat(review.getContent()).isEqualTo(CONTENT);
            assertThat(review.getStatus()).isEqualTo(ReviewStatus.ACTIVE);
        }

        @Test
        @DisplayName("projectId가 없으면 생성할 수 없다")
        void create_withoutProjectId_throws() {
            assertThatThrownBy(() -> Review.create(null, ORDER_ID, AUTHOR_ID, AUTHOR_NAME, RATING, CONTENT))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("orderId가 없으면 생성할 수 없다")
        void create_withoutOrderId_throws() {
            assertThatThrownBy(() -> Review.create(PROJECT_ID, null, AUTHOR_ID, AUTHOR_NAME, RATING, CONTENT))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("authorId가 없으면 생성할 수 없다")
        void create_withoutAuthorId_throws() {
            assertThatThrownBy(() -> Review.create(PROJECT_ID, ORDER_ID, null, AUTHOR_NAME, RATING, CONTENT))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("authorName이 없으면 생성할 수 없다")
        void create_withoutAuthorName_throws() {
            assertThatThrownBy(() -> Review.create(PROJECT_ID, ORDER_ID, AUTHOR_ID, null, RATING, CONTENT))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("authorName이 공백이면 생성할 수 없다")
        void create_blankAuthorName_throws() {
            assertThatThrownBy(() -> Review.create(PROJECT_ID, ORDER_ID, AUTHOR_ID, "   ", RATING, CONTENT))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("평점이 없으면 생성할 수 없다")
        void create_withoutRating_throws() {
            assertThatThrownBy(() -> Review.create(PROJECT_ID, ORDER_ID, AUTHOR_ID, AUTHOR_NAME, null, CONTENT))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("평점이 범위를 벗어나면 생성할 수 없다")
        void create_ratingOutOfRange_throws() {
            assertThatThrownBy(() -> Review.create(PROJECT_ID, ORDER_ID, AUTHOR_ID, AUTHOR_NAME, new BigDecimal("5.5"), CONTENT))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("평점이 소수점 둘째자리 이상이면 생성할 수 없다")
        void create_ratingTooPrecise_throws() {
            assertThatThrownBy(() -> Review.create(PROJECT_ID, ORDER_ID, AUTHOR_ID, AUTHOR_NAME, new BigDecimal("4.53"), CONTENT))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("내용이 없어도 별점만으로 생성할 수 있다")
        void create_withoutContent_success() {
            Review review = Review.create(PROJECT_ID, ORDER_ID, AUTHOR_ID, AUTHOR_NAME, RATING, null);

            assertThat(review.getContent()).isNull();
        }
    }

    @Nested
    @DisplayName("수정")
    class Update {

        @Test
        @DisplayName("작성자가 수정하면 평점/내용이 바뀌고 상태가 MODIFIED로 전이된다")
        void update_success() {
            Review review = review();

            review.update(AUTHOR_ID, new BigDecimal("3.0"), "수정된 내용");

            assertThat(review.getRating().getValue()).isEqualByComparingTo("3.0");
            assertThat(review.getContent()).isEqualTo("수정된 내용");
            assertThat(review.getStatus()).isEqualTo(ReviewStatus.MODIFIED);
        }

        @Test
        @DisplayName("작성자가 아니면 수정할 수 없다")
        void update_notOwner_throws() {
            Review review = review();

            assertThatThrownBy(() -> review.update(OTHER_AUTHOR_ID, new BigDecimal("3.0"), "수정된 내용"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("관리자여도 작성자가 아니면 수정할 수 없다")
        void update_byAdmin_throws() {
            Review review = review();

            assertThatThrownBy(() -> review.update(ADMIN_ID, new BigDecimal("3.0"), "수정된 내용"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("requesterId 없이는 수정할 수 없다")
        void update_withoutRequesterId_throws() {
            Review review = review();

            assertThatThrownBy(() -> review.update(null, new BigDecimal("3.0"), "수정된 내용"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("평점이 범위를 벗어나면 수정할 수 없다")
        void update_ratingOutOfRange_throws() {
            Review review = review();

            assertThatThrownBy(() -> review.update(AUTHOR_ID, new BigDecimal("0.5"), "수정된 내용"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("이미 삭제된 리뷰는 수정할 수 없다")
        void update_alreadyDeleted_throws() {
            Review review = review();
            review.delete(AUTHOR_ID, UserRole.CREATOR);

            assertThatThrownBy(() -> review.update(AUTHOR_ID, new BigDecimal("3.0"), "수정된 내용"))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("작성자가 삭제하면 상태가 DELETED로 전이된다")
        void delete_success() {
            Review review = review();

            review.delete(AUTHOR_ID, UserRole.CREATOR);

            assertThat(review.getStatus()).isEqualTo(ReviewStatus.DELETED);
        }

        @Test
        @DisplayName("관리자는 작성자가 아니어도 삭제할 수 있다")
        void delete_byAdmin_success() {
            Review review = review();

            review.delete(ADMIN_ID, UserRole.ADMIN);

            assertThat(review.getStatus()).isEqualTo(ReviewStatus.DELETED);
        }

        @Test
        @DisplayName("작성자도 관리자도 아니면 삭제할 수 없다")
        void delete_notOwner_throws() {
            Review review = review();

            assertThatThrownBy(() -> review.delete(OTHER_AUTHOR_ID, UserRole.CREATOR))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("requesterId 없이는 삭제할 수 없다")
        void delete_withoutRequesterId_throws() {
            Review review = review();

            assertThatThrownBy(() -> review.delete(null, UserRole.CREATOR))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("이미 삭제된 리뷰를 다시 삭제할 수 없다")
        void delete_alreadyDeleted_throws() {
            Review review = review();
            review.delete(AUTHOR_ID, UserRole.CREATOR);

            assertThatThrownBy(() -> review.delete(AUTHOR_ID, UserRole.CREATOR))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("이미 삭제된 리뷰는 관리자도 다시 삭제할 수 없다")
        void delete_alreadyDeleted_byAdmin_throws() {
            Review review = review();
            review.delete(AUTHOR_ID, UserRole.CREATOR);

            assertThatThrownBy(() -> review.delete(ADMIN_ID, UserRole.ADMIN))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    private Review review() {
        return Review.create(PROJECT_ID, ORDER_ID, AUTHOR_ID, AUTHOR_NAME, RATING, CONTENT);
    }
}