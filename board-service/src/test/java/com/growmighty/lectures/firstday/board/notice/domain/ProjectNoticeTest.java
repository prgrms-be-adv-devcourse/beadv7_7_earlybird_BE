package com.growmighty.lectures.firstday.board.notice.domain;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectNoticeTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long AUTHOR_ID = 1L;
    private static final Long OTHER_AUTHOR_ID = 2L;
    private static final Long ADMIN_ID = 99L;
    private static final String TITLE = "공지 제목";
    private static final String CONTENT = "공지 내용";

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("정상 값으로 생성하면 필드가 채워지고 ACTIVE 상태로 시작한다")
        void create_success() {
            ProjectNotice notice = ProjectNotice.create(PROJECT_ID, AUTHOR_ID, TITLE, CONTENT);

            assertThat(notice.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(notice.getAuthorId()).isEqualTo(AUTHOR_ID);
            assertThat(notice.getTitle()).isEqualTo(TITLE);
            assertThat(notice.getContent()).isEqualTo(CONTENT);
            assertThat(notice.getStatus()).isEqualTo(ProjectNoticeStatus.ACTIVE);
            assertThat(notice.getViewCount()).isZero();
        }

        @Test
        @DisplayName("projectId가 없으면 생성할 수 없다")
        void create_withoutProjectId_throws() {
            assertThatThrownBy(() -> ProjectNotice.create(null, AUTHOR_ID, TITLE, CONTENT))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("authorId가 없으면 생성할 수 없다")
        void create_withoutAuthorId_throws() {
            assertThatThrownBy(() -> ProjectNotice.create(PROJECT_ID, null, TITLE, CONTENT))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("제목이 없으면 생성할 수 없다")
        void create_withoutTitle_throws() {
            assertThatThrownBy(() -> ProjectNotice.create(PROJECT_ID, AUTHOR_ID, null, CONTENT))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("제목이 공백이면 생성할 수 없다")
        void create_blankTitle_throws() {
            assertThatThrownBy(() -> ProjectNotice.create(PROJECT_ID, AUTHOR_ID, "   ", CONTENT))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("제목이 255자를 넘으면 생성할 수 없다")
        void create_tooLongTitle_throws() {
            String tooLongTitle = "a".repeat(256);

            assertThatThrownBy(() -> ProjectNotice.create(PROJECT_ID, AUTHOR_ID, tooLongTitle, CONTENT))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("제목이 정확히 255자면 생성할 수 있다")
        void create_exactly255Title_success() {
            String maxLengthTitle = "a".repeat(255);

            ProjectNotice notice = ProjectNotice.create(PROJECT_ID, AUTHOR_ID, maxLengthTitle, CONTENT);

            assertThat(notice.getTitle()).hasSize(255);
        }

        @Test
        @DisplayName("내용이 없으면 생성할 수 없다")
        void create_withoutContent_throws() {
            assertThatThrownBy(() -> ProjectNotice.create(PROJECT_ID, AUTHOR_ID, TITLE, null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("내용이 공백이면 생성할 수 없다")
        void create_blankContent_throws() {
            assertThatThrownBy(() -> ProjectNotice.create(PROJECT_ID, AUTHOR_ID, TITLE, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("수정")
    class Update {

        @Test
        @DisplayName("작성자가 수정하면 제목/내용이 바뀌고 상태가 MODIFIED로 전이된다")
        void update_success() {
            ProjectNotice notice = notice();

            notice.update(AUTHOR_ID, UserRole.CREATOR, "수정된 제목", "수정된 내용");

            assertThat(notice.getTitle()).isEqualTo("수정된 제목");
            assertThat(notice.getContent()).isEqualTo("수정된 내용");
            assertThat(notice.getStatus()).isEqualTo(ProjectNoticeStatus.MODIFIED);
        }

        @Test
        @DisplayName("관리자는 작성자가 아니어도 수정할 수 있다")
        void update_byAdmin_success() {
            ProjectNotice notice = notice();

            notice.update(ADMIN_ID, UserRole.ADMIN, "수정된 제목", "수정된 내용");

            assertThat(notice.getTitle()).isEqualTo("수정된 제목");
            assertThat(notice.getContent()).isEqualTo("수정된 내용");
            assertThat(notice.getStatus()).isEqualTo(ProjectNoticeStatus.MODIFIED);
        }

        @Test
        @DisplayName("작성자도 관리자도 아니면 수정할 수 없다")
        void update_notOwner_throws() {
            ProjectNotice notice = notice();

            assertThatThrownBy(() -> notice.update(OTHER_AUTHOR_ID, UserRole.CREATOR, "수정된 제목", "수정된 내용"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("authorId 없이는 수정할 수 없다")
        void update_withoutAuthorId_throws() {
            ProjectNotice notice = notice();

            assertThatThrownBy(() -> notice.update(null, UserRole.CREATOR, "수정된 제목", "수정된 내용"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("제목이 공백이면 수정할 수 없다")
        void update_blankTitle_throws() {
            ProjectNotice notice = notice();

            assertThatThrownBy(() -> notice.update(AUTHOR_ID, UserRole.CREATOR, "   ", CONTENT))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("내용이 공백이면 수정할 수 없다")
        void update_blankContent_throws() {
            ProjectNotice notice = notice();

            assertThatThrownBy(() -> notice.update(AUTHOR_ID, UserRole.CREATOR, TITLE, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("이미 삭제된 공지는 수정할 수 없다")
        void update_alreadyDeleted_throws() {
            ProjectNotice notice = notice();
            notice.delete(AUTHOR_ID, UserRole.CREATOR);

            assertThatThrownBy(() -> notice.update(AUTHOR_ID, UserRole.CREATOR, "수정된 제목", "수정된 내용"))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("이미 삭제된 공지는 관리자도 수정할 수 없다")
        void update_alreadyDeleted_byAdmin_throws() {
            ProjectNotice notice = notice();
            notice.delete(AUTHOR_ID, UserRole.CREATOR);

            assertThatThrownBy(() -> notice.update(ADMIN_ID, UserRole.ADMIN, "수정된 제목", "수정된 내용"))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("삭제")
    class Delete {

        @Test
        @DisplayName("작성자가 삭제하면 상태가 DELETED로 전이된다")
        void delete_success() {
            ProjectNotice notice = notice();

            notice.delete(AUTHOR_ID, UserRole.CREATOR);

            assertThat(notice.getStatus()).isEqualTo(ProjectNoticeStatus.DELETED);
        }

        @Test
        @DisplayName("관리자는 작성자가 아니어도 삭제할 수 있다")
        void delete_byAdmin_success() {
            ProjectNotice notice = notice();

            notice.delete(ADMIN_ID, UserRole.ADMIN);

            assertThat(notice.getStatus()).isEqualTo(ProjectNoticeStatus.DELETED);
        }

        @Test
        @DisplayName("작성자도 관리자도 아니면 삭제할 수 없다")
        void delete_notOwner_throws() {
            ProjectNotice notice = notice();

            assertThatThrownBy(() -> notice.delete(OTHER_AUTHOR_ID, UserRole.CREATOR))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("authorId 없이는 삭제할 수 없다")
        void delete_withoutAuthorId_throws() {
            ProjectNotice notice = notice();

            assertThatThrownBy(() -> notice.delete(null, UserRole.CREATOR))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("이미 삭제된 공지를 다시 삭제할 수 없다")
        void delete_alreadyDeleted_throws() {
            ProjectNotice notice = notice();
            notice.delete(AUTHOR_ID, UserRole.CREATOR);

            assertThatThrownBy(() -> notice.delete(AUTHOR_ID, UserRole.CREATOR))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("이미 삭제된 공지는 관리자도 다시 삭제할 수 없다")
        void delete_alreadyDeleted_byAdmin_throws() {
            ProjectNotice notice = notice();
            notice.delete(AUTHOR_ID, UserRole.CREATOR);

            assertThatThrownBy(() -> notice.delete(ADMIN_ID, UserRole.ADMIN))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    private ProjectNotice notice() {
        return ProjectNotice.create(PROJECT_ID, AUTHOR_ID, TITLE, CONTENT);
    }
}