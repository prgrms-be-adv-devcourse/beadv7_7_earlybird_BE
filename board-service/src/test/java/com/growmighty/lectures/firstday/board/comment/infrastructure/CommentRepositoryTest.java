package com.growmighty.lectures.firstday.board.comment.infrastructure;

import com.growmighty.lectures.firstday.board.config.JpaAuditingConfig;
import com.growmighty.lectures.firstday.board.comment.domain.Comment;
import com.growmighty.lectures.firstday.board.comment.domain.CommentRepository;
import com.growmighty.lectures.firstday.board.comment.domain.CommentStatus;
import com.growmighty.lectures.firstday.board.comment.domain.CommentTargetType;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
// 내장 DB 가 아니면 Boot 가 ddl-auto 를 기본 none 으로 두므로, 테스트 스키마 생성을 명시한다.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
// JpaAuditingConfig: @DataJpaTest 도 일반 @Configuration 빈은 스캔에서 걸러내므로
// created_at/updated_at 을 실제로 채우려면 명시적으로 가져와야 한다.
@Import({CommentRepositoryAdapter.class, JpaAuditingConfig.class})
class CommentRepositoryTest {

    // 테스트도 운영과 동일한 MySQL 로 돈다 (로컬 docker-compose 와 동일 버전, Docker 필요)
    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private EntityManager entityManager;

    private static final CommentTargetType TARGET_TYPE = CommentTargetType.PROJECT;
    private static final Long TARGET_ID = 1L;
    private static final Long OTHER_TARGET_ID = 2L;
    private static final Long AUTHOR_ID = 1L;

    @Test
    @DisplayName("의견을 저장하고 조회하면 감사 필드(createdAt/updatedAt)까지 채워져 있다")
    void saveAndFindById() {
        Comment comment = Comment.create(TARGET_TYPE, TARGET_ID, AUTHOR_ID, "내용");

        Comment saved = commentRepository.save(comment);
        entityManager.flush();
        entityManager.clear();

        Comment found = commentRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getTargetType()).isEqualTo(TARGET_TYPE);
        assertThat(found.getTargetId()).isEqualTo(TARGET_ID);
        assertThat(found.getStatus()).isEqualTo(CommentStatus.ACTIVE);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("findById는 삭제된 의견도 그대로 반환한다 (update/delete가 '이미 삭제됨'과 '존재한 적 없음'을 구분하기 위해 의도적으로 필터링하지 않음)")
    void findByIdReturnsDeletedComment() {
        Comment comment = commentRepository.save(Comment.create(TARGET_TYPE, TARGET_ID, AUTHOR_ID, "내용"));
        comment.delete(AUTHOR_ID, UserRole.BACKER);
        entityManager.flush();
        entityManager.clear();

        Comment found = commentRepository.findById(comment.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(CommentStatus.DELETED);
    }

    @Nested
    @DisplayName("findVisibleByTargetTypeAndTargetId")
    class FindVisibleByTargetTypeAndTargetId {

        @Test
        @DisplayName("삭제된 의견은 목록에서 제외한다")
        void excludesDeleted() {
            Comment visible = commentRepository.save(Comment.create(TARGET_TYPE, TARGET_ID, AUTHOR_ID, "안 지워짐"));
            Comment deleted = commentRepository.save(Comment.create(TARGET_TYPE, TARGET_ID, AUTHOR_ID, "지워짐"));
            deleted.delete(AUTHOR_ID, UserRole.BACKER);
            entityManager.flush();
            entityManager.clear();

            List<Comment> result = commentRepository.findVisibleByTargetTypeAndTargetId(TARGET_TYPE, TARGET_ID);

            assertThat(result).extracting(Comment::getId).containsExactly(visible.getId());
        }

        @Test
        @DisplayName("수정된(MODIFIED) 의견은 목록에 그대로 남는다")
        void includesModified() {
            Comment comment = commentRepository.save(Comment.create(TARGET_TYPE, TARGET_ID, AUTHOR_ID, "내용"));
            comment.update(AUTHOR_ID, "수정된 내용");
            entityManager.flush();
            entityManager.clear();

            List<Comment> result = commentRepository.findVisibleByTargetTypeAndTargetId(TARGET_TYPE, TARGET_ID);

            assertThat(result).extracting(Comment::getId).containsExactly(comment.getId());
            assertThat(result.get(0).getStatus()).isEqualTo(CommentStatus.MODIFIED);
        }

        @Test
        @DisplayName("대댓글도 부모와 같은 target 목록에 포함된다 (평탄한 목록, 그룹핑은 표현 계층 몫)")
        void includesReplies() {
            Comment parent = commentRepository.save(Comment.create(TARGET_TYPE, TARGET_ID, AUTHOR_ID, "댓글"));
            Comment reply = commentRepository.save(Comment.reply(parent, AUTHOR_ID, "대댓글"));
            entityManager.flush();
            entityManager.clear();

            List<Comment> result = commentRepository.findVisibleByTargetTypeAndTargetId(TARGET_TYPE, TARGET_ID);

            assertThat(result).extracting(Comment::getId).containsExactlyInAnyOrder(parent.getId(), reply.getId());
        }

        @Test
        @DisplayName("다른 targetId의 의견은 섞이지 않는다")
        void scopedToTargetId() {
            commentRepository.save(Comment.create(TARGET_TYPE, TARGET_ID, AUTHOR_ID, "이 대상"));
            commentRepository.save(Comment.create(TARGET_TYPE, OTHER_TARGET_ID, AUTHOR_ID, "다른 대상"));
            entityManager.flush();
            entityManager.clear();

            List<Comment> result = commentRepository.findVisibleByTargetTypeAndTargetId(TARGET_TYPE, TARGET_ID);

            assertThat(result).allMatch(comment -> comment.getTargetId().equals(TARGET_ID));
        }

        @Test
        @DisplayName("targetId가 같아도 targetType이 다르면 섞이지 않는다")
        void scopedToTargetType() {
            commentRepository.save(Comment.create(CommentTargetType.PROJECT, TARGET_ID, AUTHOR_ID, "프로젝트 본문"));
            commentRepository.save(Comment.create(CommentTargetType.PROJECT_NOTICE, TARGET_ID, AUTHOR_ID, "공지"));
            entityManager.flush();
            entityManager.clear();

            List<Comment> result = commentRepository.findVisibleByTargetTypeAndTargetId(CommentTargetType.PROJECT, TARGET_ID);

            assertThat(result).allMatch(comment -> comment.getTargetType() == CommentTargetType.PROJECT);
        }

        @Test
        @DisplayName("최신순(createdAt 내림차순)으로 정렬된다")
        void orderedByCreatedAtDesc() throws InterruptedException {
            Comment first = commentRepository.save(Comment.create(TARGET_TYPE, TARGET_ID, AUTHOR_ID, "첫 번째"));
            // createdAt은 persist 시점의 LocalDateTime.now()라, 두 건이 같은 값을 갖지 않도록 간격을 둔다.
            Thread.sleep(10);
            Comment second = commentRepository.save(Comment.create(TARGET_TYPE, TARGET_ID, AUTHOR_ID, "두 번째"));
            entityManager.flush();
            entityManager.clear();

            List<Comment> result = commentRepository.findVisibleByTargetTypeAndTargetId(TARGET_TYPE, TARGET_ID);

            assertThat(result).extracting(Comment::getId)
                .containsExactly(second.getId(), first.getId());
        }
    }
}