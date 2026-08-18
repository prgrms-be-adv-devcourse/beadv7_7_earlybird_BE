package com.growmighty.lectures.firstday.file.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.file.config.JpaAuditingConfig;
import com.growmighty.lectures.firstday.file.domain.File;
import com.growmighty.lectures.firstday.file.domain.FileOwnerType;
import com.growmighty.lectures.firstday.file.domain.FileRepository;
import com.growmighty.lectures.firstday.file.support.MySqlIntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create")
@Import({JpaAuditingConfig.class, FileRepositoryAdapter.class})
class FileSoftDeleteIntegrationTest extends MySqlIntegrationTestSupport {

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("deleteById는 물리 삭제 대신 deleted_at을 채우고, 이후 조회에서는 제외된다")
    void deleteByIdSoftDeletes() {
        File saved = fileRepository.save(
            File.register(FileOwnerType.PROJECT, 1L, 10L, "https://cdn.example.com/files/10/a.jpg", "a.jpg",
                "image/jpeg", 1024L, 0));
        entityManager.flush();
        entityManager.clear();

        fileRepository.deleteById(saved.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(fileRepository.findById(saved.getId())).isEmpty();
        assertThat(fileRepository.findByOwnerTypeAndOwnerId(FileOwnerType.PROJECT, 1L)).isEmpty();

        Object deletedAt = entityManager.createNativeQuery("SELECT deleted_at FROM files WHERE id = ?1")
            .setParameter(1, saved.getId())
            .getSingleResult();
        assertThat(deletedAt).isNotNull();
    }
}
