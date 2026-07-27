package com.growmighty.lectures.firstday.board.notice.infrastructure;

import com.growmighty.lectures.firstday.board.config.JpaAuditingConfig;
import com.growmighty.lectures.firstday.board.notice.domain.ProjectNoticeRepository;
import com.growmighty.lectures.firstday.board.notice.domain.ProjectNotice;
import com.growmighty.lectures.firstday.board.notice.domain.ProjectNoticeStatus;
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
@Import({ProjectNoticeRepositoryAdapter.class, JpaAuditingConfig.class})
class ProjectNoticeRepositoryTest {

    // 테스트도 운영과 동일한 MySQL 로 돈다 (로컬 docker-compose 와 동일 버전, Docker 필요)
    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ProjectNoticeRepository projectNoticeRepository;

    @Autowired
    private EntityManager entityManager;

    private static final Long PROJECT_ID = 1L;
    private static final Long OTHER_PROJECT_ID = 2L;
    private static final Long AUTHOR_ID = 1L;
    private static final String AUTHOR_NAME = "작성자";

    @Test
    @DisplayName("공지를 저장하고 조회하면 감사 필드(createdAt/updatedAt)까지 채워져 있다")
    void saveAndFindById() {
        ProjectNotice notice = ProjectNotice.create(PROJECT_ID, AUTHOR_ID, AUTHOR_NAME, "제목", "내용");

        ProjectNotice saved = projectNoticeRepository.save(notice);
        entityManager.flush();
        entityManager.clear();

        ProjectNotice found = projectNoticeRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(found.getTitle()).isEqualTo("제목");
        assertThat(found.getStatus()).isEqualTo(ProjectNoticeStatus.ACTIVE);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("findById는 삭제된 공지도 그대로 반환한다 (update/delete가 '이미 삭제됨'과 '존재한 적 없음'을 구분하기 위해 의도적으로 필터링하지 않음)")
    void findByIdReturnsDeletedNotice() {
        ProjectNotice notice = projectNoticeRepository.save(ProjectNotice.create(PROJECT_ID, AUTHOR_ID, AUTHOR_NAME, "제목", "내용"));
        notice.delete(AUTHOR_ID, UserRole.CREATOR);
        entityManager.flush();
        entityManager.clear();

        ProjectNotice found = projectNoticeRepository.findById(notice.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(ProjectNoticeStatus.DELETED);
    }

    @Nested
    @DisplayName("findVisibleByProjectId")
    class FindVisibleByProjectId {

        @Test
        @DisplayName("삭제된 공지는 목록에서 제외한다")
        void excludesDeleted() {
            ProjectNotice visible = projectNoticeRepository.save(ProjectNotice.create(PROJECT_ID, AUTHOR_ID, AUTHOR_NAME, "안 지워짐", "내용"));
            ProjectNotice deleted = projectNoticeRepository.save(ProjectNotice.create(PROJECT_ID, AUTHOR_ID, AUTHOR_NAME, "지워짐", "내용"));
            deleted.delete(AUTHOR_ID, UserRole.CREATOR);
            entityManager.flush();
            entityManager.clear();

            List<ProjectNotice> result = projectNoticeRepository.findVisibleByProjectId(PROJECT_ID);

            assertThat(result).extracting(ProjectNotice::getId).containsExactly(visible.getId());
        }

        @Test
        @DisplayName("수정된(MODIFIED) 공지는 목록에 그대로 남는다")
        void includesModified() {
            ProjectNotice notice = projectNoticeRepository.save(ProjectNotice.create(PROJECT_ID, AUTHOR_ID, AUTHOR_NAME, "제목", "내용"));
            notice.update(AUTHOR_ID, UserRole.CREATOR, "수정된 제목", "수정된 내용");
            entityManager.flush();
            entityManager.clear();

            List<ProjectNotice> result = projectNoticeRepository.findVisibleByProjectId(PROJECT_ID);

            assertThat(result).extracting(ProjectNotice::getId).containsExactly(notice.getId());
            assertThat(result.get(0).getStatus()).isEqualTo(ProjectNoticeStatus.MODIFIED);
        }

        @Test
        @DisplayName("다른 프로젝트의 공지는 섞이지 않는다")
        void scopedToProject() {
            projectNoticeRepository.save(ProjectNotice.create(PROJECT_ID, AUTHOR_ID, AUTHOR_NAME, "이 프로젝트", "내용"));
            projectNoticeRepository.save(ProjectNotice.create(OTHER_PROJECT_ID, AUTHOR_ID, AUTHOR_NAME, "다른 프로젝트", "내용"));
            entityManager.flush();
            entityManager.clear();

            List<ProjectNotice> result = projectNoticeRepository.findVisibleByProjectId(PROJECT_ID);

            assertThat(result).allMatch(notice -> notice.getProjectId().equals(PROJECT_ID));
        }

        @Test
        @DisplayName("최신순(createdAt 내림차순)으로 정렬된다")
        void orderedByCreatedAtDesc() throws InterruptedException {
            ProjectNotice first = projectNoticeRepository.save(ProjectNotice.create(PROJECT_ID, AUTHOR_ID, AUTHOR_NAME, "첫 번째", "내용"));
            // createdAt은 persist 시점의 LocalDateTime.now()라, 두 건이 같은 값을 갖지 않도록 간격을 둔다.
            Thread.sleep(10);
            ProjectNotice second = projectNoticeRepository.save(ProjectNotice.create(PROJECT_ID, AUTHOR_ID, AUTHOR_NAME, "두 번째", "내용"));
            entityManager.flush();
            entityManager.clear();

            List<ProjectNotice> result = projectNoticeRepository.findVisibleByProjectId(PROJECT_ID);

            assertThat(result).extracting(ProjectNotice::getId)
                .containsExactly(second.getId(), first.getId());
        }
    }

    @Nested
    @DisplayName("existsVisibleById")
    class ExistsVisibleById {

        @Test
        @DisplayName("존재하는 공지면 true다")
        void trueWhenExists() {
            ProjectNotice notice = projectNoticeRepository.save(ProjectNotice.create(PROJECT_ID, AUTHOR_ID, AUTHOR_NAME, "제목", "내용"));
            entityManager.flush();
            entityManager.clear();

            assertThat(projectNoticeRepository.existsVisibleById(notice.getId())).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 id면 false다")
        void falseWhenNotFound() {
            assertThat(projectNoticeRepository.existsVisibleById(999L)).isFalse();
        }

        @Test
        @DisplayName("삭제된 공지는 false다 — 댓글 대상으로 취급하지 않는다")
        void falseWhenDeleted() {
            ProjectNotice notice = projectNoticeRepository.save(ProjectNotice.create(PROJECT_ID, AUTHOR_ID, AUTHOR_NAME, "제목", "내용"));
            notice.delete(AUTHOR_ID, UserRole.CREATOR);
            entityManager.flush();
            entityManager.clear();

            assertThat(projectNoticeRepository.existsVisibleById(notice.getId())).isFalse();
        }
    }
}