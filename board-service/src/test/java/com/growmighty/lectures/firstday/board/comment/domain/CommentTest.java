package com.growmighty.lectures.firstday.board.comment.domain;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentTest {

    private static final CommentTargetType TARGET_TYPE = CommentTargetType.PROJECT;
    private static final Long TARGET_ID = 1L;
    private static final Long AUTHOR_ID = 1L;
    private static final Long OTHER_AUTHOR_ID = 2L;
    private static final Long ADMIN_ID = 99L;
    private static final String CONTENT = "댓글 내용";

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("정상 값으로 생성하면 필드가 채워지고 ACTIVE 상태로 시작하며 parentId는 없다")
        void create_success() {
            Comment comment = Comment.create(TARGET_TYPE, TARGET_ID, AUTHOR_ID, CONTENT);

            assertThat(comment.getTargetType()).isEqualTo(TARGET_TYPE);
            assertThat(comment.getTargetId()).isEqualTo(TARGET_ID);
            assertThat(comment.getAuthorId()).isEqualTo(AUTHOR_ID);
            assertThat(comment.getContent()).isEqualTo(CONTENT);
            assertThat(comment.getStatus()).isEqualTo(CommentStatus.ACTIVE);
            assertThat(comment.getParentId()).isNull();
        }

        @Test
        @DisplayName("targetType이 없으면 생성할 수 없다")
        void create_withoutTargetType_throws() {
            assertThatThrownBy(() -> Comment.create(null, TARGET_ID, AUTHOR_ID, CONTENT))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("targetId가 없으면 생성할 수 없다")
        void create_withoutTargetId_throws() {
            assertThatThrownBy(() -> Comment.create(TARGET_TYPE, null, AUTHOR_ID, CONTENT))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("authorId가 없으면 생성할 수 없다")
        void create_withoutAuthorId_throws() {
            assertThatThrownBy(() -> Comment.create(TARGET_TYPE, TARGET_ID, null, CONTENT))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("내용이 없으면 생성할 수 없다")
        void create_withoutContent_throws() {
            assertThatThrownBy(() -> Comment.create(TARGET_TYPE, TARGET_ID, AUTHOR_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("내용이 공백이면 생성할 수 없다")
        void create_blankContent_throws() {
            assertThatThrownBy(() -> Comment.create(TARGET_TYPE, TARGET_ID, AUTHOR_ID, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("답글")
    class Reply {

        @Test
        @DisplayName("루트 댓글에 답글을 달면 target과 parentId를 부모로부터 물려받는다")
        void reply_success() {
            Comment parent = comment();
            ReflectionTestUtils.setField(parent, "id", 1L);

            Comment reply = Comment.reply(parent, OTHER_AUTHOR_ID, "답글 내용");

            assertThat(reply.getTargetType()).isEqualTo(parent.getTargetType());
            assertThat(reply.getTargetId()).isEqualTo(parent.getTargetId());
            assertThat(reply.getParentId()).isEqualTo(parent.getId());
            assertThat(reply.getAuthorId()).isEqualTo(OTHER_AUTHOR_ID);
            assertThat(reply.getStatus()).isEqualTo(CommentStatus.ACTIVE);
        }

        @Test
        @DisplayName("답글에는 답글을 달 수 없다 (대댓글 1단 제한)")
        void reply_toReply_throws() {
            Comment parent = comment();
            ReflectionTestUtils.setField(parent, "id", 1L);
            Comment reply = Comment.reply(parent, OTHER_AUTHOR_ID, "답글 내용");

            assertThatThrownBy(() -> Comment.reply(reply, AUTHOR_ID, "대대댓글 내용"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("답글도 authorId 없이는 달 수 없다")
        void reply_withoutAuthorId_throws() {
            Comment parent = comment();

            assertThatThrownBy(() -> Comment.reply(parent, null, "답글 내용"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("답글도 내용이 공백이면 달 수 없다")
        void reply_blankContent_throws() {
            Comment parent = comment();

            assertThatThrownBy(() -> Comment.reply(parent, OTHER_AUTHOR_ID, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("수정")
    class Update {

        @Test
        @DisplayName("작성자가 수정하면 내용이 바뀌고 상태가 MODIFIED로 전이된다")
        void update_success() {
            Comment comment = comment();

            comment.update(AUTHOR_ID, "수정된 내용");

            assertThat(comment.getContent()).isEqualTo("수정된 내용");
            assertThat(comment.getStatus()).isEqualTo(CommentStatus.MODIFIED);
        }

        @Test
        @DisplayName("작성자가 아니면 관리자여도 수정할 수 없다")
        void update_notAuthor_throws() {
            Comment comment = comment();

            assertThatThrownBy(() -> comment.update(OTHER_AUTHOR_ID, "수정된 내용"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("requesterId 없이는 수정할 수 없다")
        void update_withoutRequesterId_throws() {
            Comment comment = comment();

            assertThatThrownBy(() -> comment.update(null, "수정된 내용"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("내용이 공백이면 수정할 수 없다")
        void update_blankContent_throws() {
            Comment comment = comment();

            assertThatThrownBy(() -> comment.update(AUTHOR_ID, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("이미 삭제된 댓글은 수정할 수 없다")
        void update_alreadyDeleted_throws() {
            Comment comment = comment();
            comment.delete(AUTHOR_ID, UserRole.BACKER);

            assertThatThrownBy(() -> comment.update(AUTHOR_ID, "수정된 내용"))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("작성자가 삭제하면 상태가 DELETED로 전이된다")
        void delete_success() {
            Comment comment = comment();

            comment.delete(AUTHOR_ID, UserRole.BACKER);

            assertThat(comment.getStatus()).isEqualTo(CommentStatus.DELETED);
        }

        @Test
        @DisplayName("관리자는 작성자가 아니어도 삭제할 수 있다")
        void delete_byAdmin_success() {
            Comment comment = comment();

            comment.delete(ADMIN_ID, UserRole.ADMIN);

            assertThat(comment.getStatus()).isEqualTo(CommentStatus.DELETED);
        }

        @Test
        @DisplayName("작성자도 관리자도 아니면 삭제할 수 없다")
        void delete_notOwner_throws() {
            Comment comment = comment();

            assertThatThrownBy(() -> comment.delete(OTHER_AUTHOR_ID, UserRole.BACKER))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("requesterId 없이는 삭제할 수 없다")
        void delete_withoutRequesterId_throws() {
            Comment comment = comment();

            assertThatThrownBy(() -> comment.delete(null, UserRole.BACKER))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("이미 삭제된 댓글을 다시 삭제할 수 없다")
        void delete_alreadyDeleted_throws() {
            Comment comment = comment();
            comment.delete(AUTHOR_ID, UserRole.BACKER);

            assertThatThrownBy(() -> comment.delete(AUTHOR_ID, UserRole.BACKER))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("이미 삭제된 댓글은 관리자도 다시 삭제할 수 없다")
        void delete_alreadyDeleted_byAdmin_throws() {
            Comment comment = comment();
            comment.delete(AUTHOR_ID, UserRole.BACKER);

            assertThatThrownBy(() -> comment.delete(ADMIN_ID, UserRole.ADMIN))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    private Comment comment() {
        return Comment.create(TARGET_TYPE, TARGET_ID, AUTHOR_ID, CONTENT);
    }
}