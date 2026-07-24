package com.growmighty.lectures.firstday.board.review.infrastructure;

import com.growmighty.lectures.firstday.board.config.JpaAuditingConfig;
import com.growmighty.lectures.firstday.board.review.domain.Review;
import com.growmighty.lectures.firstday.board.review.domain.ReviewRepository;
import com.growmighty.lectures.firstday.board.review.domain.ReviewStatus;
import com.growmighty.lectures.firstday.common.entity.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
// 내장 DB 가 아니면 Boot 가 ddl-auto 를 기본 none 으로 두므로, 테스트 스키마 생성을 명시한다.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
// JpaAuditingConfig: @DataJpaTest 도 일반 @Configuration 빈은 스캔에서 걸러내므로
// created_at/updated_at 을 실제로 채우려면 명시적으로 가져와야 한다.
@Import({ReviewRepositoryAdapter.class, JpaAuditingConfig.class})
class ReviewRepositoryTest {

    // 테스트도 운영과 동일한 MySQL 로 돈다 (로컬 docker-compose 와 동일 버전, Docker 필요)
    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private EntityManager entityManager;

    private static final Long PROJECT_ID = 1L;
    private static final Long OTHER_PROJECT_ID = 2L;
    private static final Long ORDER_ID = 1L;
    private static final Long AUTHOR_ID = 1L;
    private static final String AUTHOR_NAME = "작성자";
    private static final BigDecimal RATING = BigDecimal.valueOf(4.5);

    @Test
    @DisplayName("리뷰를 저장하고 조회하면 감사 필드(createdAt/updatedAt)까지 채워져 있다")
    void saveAndFindById() {
        Review review = Review.create(PROJECT_ID, ORDER_ID, AUTHOR_ID, AUTHOR_NAME, RATING, "내용");

        Review saved = reviewRepository.save(review);
        entityManager.flush();
        entityManager.clear();

        Review found = reviewRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(found.getRating().getValue()).isEqualByComparingTo(RATING);
        assertThat(found.getStatus()).isEqualTo(ReviewStatus.ACTIVE);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("findById는 삭제된 리뷰도 그대로 반환한다 (update/delete가 '이미 삭제됨'과 '존재한 적 없음'을 구분하기 위해 의도적으로 필터링하지 않음)")
    void findByIdReturnsDeletedReview() {
        Review review = reviewRepository.save(Review.create(PROJECT_ID, ORDER_ID, AUTHOR_ID, AUTHOR_NAME, RATING, "내용"));
        review.delete(AUTHOR_ID, UserRole.BACKER);
        entityManager.flush();
        entityManager.clear();

        Review found = reviewRepository.findById(review.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(ReviewStatus.DELETED);
    }

    @Nested
    @DisplayName("findVisibleByProjectId")
    class FindVisibleByProjectId {

        @Test
        @DisplayName("삭제된 리뷰는 목록에서 제외한다")
        void excludesDeleted() {
            Review visible = reviewRepository.save(Review.create(PROJECT_ID, ORDER_ID, AUTHOR_ID, AUTHOR_NAME, RATING, "안 지워짐"));
            Review deleted = reviewRepository.save(Review.create(PROJECT_ID, ORDER_ID, AUTHOR_ID, AUTHOR_NAME, RATING, "지워짐"));
            deleted.delete(AUTHOR_ID, UserRole.BACKER);
            entityManager.flush();
            entityManager.clear();

            List<Review> result = reviewRepository.findVisibleByProjectId(PROJECT_ID);

            assertThat(result).extracting(Review::getId).containsExactly(visible.getId());
        }

        @Test
        @DisplayName("수정된(MODIFIED) 리뷰는 목록에 그대로 남는다")
        void includesModified() {
            Review review = reviewRepository.save(Review.create(PROJECT_ID, ORDER_ID, AUTHOR_ID, AUTHOR_NAME, RATING, "내용"));
            review.update(AUTHOR_ID, BigDecimal.valueOf(3.0), "수정된 내용");
            entityManager.flush();
            entityManager.clear();

            List<Review> result = reviewRepository.findVisibleByProjectId(PROJECT_ID);

            assertThat(result).extracting(Review::getId).containsExactly(review.getId());
            assertThat(result.get(0).getStatus()).isEqualTo(ReviewStatus.MODIFIED);
        }

        @Test
        @DisplayName("다른 프로젝트의 리뷰는 섞이지 않는다")
        void scopedToProject() {
            reviewRepository.save(Review.create(PROJECT_ID, ORDER_ID, AUTHOR_ID, AUTHOR_NAME, RATING, "이 프로젝트"));
            reviewRepository.save(Review.create(OTHER_PROJECT_ID, ORDER_ID, AUTHOR_ID, AUTHOR_NAME, RATING, "다른 프로젝트"));
            entityManager.flush();
            entityManager.clear();

            List<Review> result = reviewRepository.findVisibleByProjectId(PROJECT_ID);

            assertThat(result).allMatch(review -> review.getProjectId().equals(PROJECT_ID));
        }

        @Test
        @DisplayName("최신순(createdAt 내림차순)으로 정렬된다")
        void orderedByCreatedAtDesc() throws InterruptedException {
            Review first = reviewRepository.save(Review.create(PROJECT_ID, ORDER_ID, AUTHOR_ID, AUTHOR_NAME, RATING, "첫 번째"));
            // createdAt은 persist 시점의 LocalDateTime.now()라, 두 건이 같은 값을 갖지 않도록 간격을 둔다.
            Thread.sleep(10);
            Review second = reviewRepository.save(Review.create(PROJECT_ID, ORDER_ID, AUTHOR_ID, AUTHOR_NAME, RATING, "두 번째"));
            entityManager.flush();
            entityManager.clear();

            List<Review> result = reviewRepository.findVisibleByProjectId(PROJECT_ID);

            assertThat(result).extracting(Review::getId)
                .containsExactly(second.getId(), first.getId());
        }
    }
}