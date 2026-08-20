package com.growmighty.lectures.firstday.user.infrastructure;

import com.growmighty.lectures.firstday.user.config.JpaAuditingConfig;
import com.growmighty.lectures.firstday.user.config.UserSecurityConfig;
import com.growmighty.lectures.firstday.user.domain.CreatorProfile;
import com.growmighty.lectures.firstday.user.domain.User;
import com.growmighty.lectures.firstday.user.infrastructure.security.UserSensitiveDataCrypto;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 서비스 계층의 existsByEmail/existsByUserId 중복 검사를 우회하는 경합이 생겨도,
 * DB 유니크 제약이 최후의 방어선으로 실제 작동하는지 확인한다.
 */
@Testcontainers
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create")
@Import({JpaAuditingConfig.class, UserSecurityConfig.class, UserSensitiveDataCrypto.class})
class UserPersistenceTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private CreatorProfileJpaRepository creatorProfileJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("같은 이메일로 유저를 두 번 저장할 수 없다")
    void rejectsDuplicateEmail() {
        userJpaRepository.saveAndFlush(User.register("dup@example.com", "encoded", "김하나한", "010-0000-0000"));
        entityManager.clear();

        User duplicate = User.register("dup@example.com", "encoded", "다른이름", "010-1111-1111");

        assertThatThrownBy(() -> userJpaRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 userId 로 정산 계좌 정보를 두 번 저장할 수 없다")
    void rejectsDuplicateCreatorProfileUserId() {
        User user = userJpaRepository.saveAndFlush(
                User.register("creator@example.com", "encoded", "창작자", "010-2222-2222"));
        entityManager.clear();
        creatorProfileJpaRepository.saveAndFlush(
                CreatorProfile.register(user.getId(), "신한은행", "110-123-456789", "창작자"));
        entityManager.clear();

        CreatorProfile duplicate = CreatorProfile.register(user.getId(), "국민은행", "220-987-654321", "창작자");

        assertThatThrownBy(() -> creatorProfileJpaRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
