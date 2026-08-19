package com.growmighty.lectures.firstday.refund.infrastructure;

import com.growmighty.lectures.firstday.payment.PaymentServiceApplication;
import com.growmighty.lectures.firstday.payment.config.JpaAuditingConfig;
import com.growmighty.lectures.firstday.payment.config.PaymentSecurityConfig;
import com.growmighty.lectures.firstday.payment.infrastructure.security.PaymentSensitiveDataCrypto;
import com.growmighty.lectures.firstday.refund.domain.BulkRefundResultOutbox;
import com.growmighty.lectures.firstday.refund.domain.BulkRefundResultStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create",
    "spring.cloud.config.enabled=false",
    "payment.security.encryption-key=MDEyMzQ1Njc4OUFCQ0RFRjAxMjM0NTY3ODlBQkNERUY="
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PaymentServiceApplication.class)
@Import({
    JpaAuditingConfig.class,
    PaymentSecurityConfig.class,
    PaymentSensitiveDataCrypto.class
})
class BulkRefundResultOutboxJpaRepositoryTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private BulkRefundResultOutboxJpaRepository outboxJpaRepository;

    @Autowired
    private EntityManager entityManager;

    // 추가 : 같은 refundRequestId와 결과 상태의 중복 insert는 하나의 Outbox만 남긴다.
    @Test
    void insertIfAbsent_ignoresDuplicateRefundResultOutbox() {
        outboxJpaRepository.insertIfAbsent(1L, BulkRefundResultStatus.COMPLETED.getCode());
        outboxJpaRepository.insertIfAbsent(1L, BulkRefundResultStatus.COMPLETED.getCode());

        entityManager.flush();
        entityManager.clear();

        assertThat(outboxJpaRepository.findAll())
            .singleElement()
            .extracting(BulkRefundResultOutbox::getRefundRequestId, BulkRefundResultOutbox::getResultStatus)
            .containsExactly(1L, BulkRefundResultStatus.COMPLETED);
    }
}
