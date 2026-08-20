package com.growmighty.lectures.firstday.refund.infrastructure;

import com.growmighty.lectures.firstday.payment.PaymentServiceApplication;
import com.growmighty.lectures.firstday.payment.config.JpaAuditingConfig;
import com.growmighty.lectures.firstday.payment.config.PaymentSecurityConfig;
import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.infrastructure.PaymentJpaRepository;
import com.growmighty.lectures.firstday.payment.infrastructure.security.PaymentSensitiveDataCrypto;
import com.growmighty.lectures.firstday.refund.application.dto.RefundRecoveryTarget;
import com.growmighty.lectures.firstday.refund.application.port.RefundRecoveryTargetReader;
import com.growmighty.lectures.firstday.refund.domain.Refund;
import com.growmighty.lectures.firstday.refund.domain.RefundReason;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create",
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "spring.cloud.config.enabled=false",
    "payment.security.encryption-key=MDEyMzQ1Njc4OUFCQ0RFRjAxMjM0NTY3ODlBQkNERUY=" // <-- 테스트 전용 32바이트 AES 키
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PaymentServiceApplication.class) // <-- payment-service 설정 클래스 명시
@Import({
    JpaAuditingConfig.class,
    PaymentSecurityConfig.class,
    PaymentSensitiveDataCrypto.class,
    RefundRecoveryTargetReaderAdapter.class
})
class RefundRecoveryTargetReaderAdapterTest {

    private static final int BATCH_SIZE = 100;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private RefundRecoveryTargetReader refundRecoveryTargetReader;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private RefundJpaRepository refundJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void 시간_초과된_REQUESTED_환불_100건을_단일_조회로_가져온다() {
        for (long orderId = 1; orderId <= BATCH_SIZE; orderId++) {
            Payment payment = Payment.ready(1L, orderId, BigDecimal.TEN);
            payment.startConfirming("payment-key-" + orderId);
            payment.confirm("payment-key-" + orderId);
            paymentJpaRepository.save(payment);

            refundJpaRepository.save(Refund.request(
                payment.getPaymentId(),
                BigDecimal.TEN,
                RefundReason.USER_CANCEL
            ));
        }

        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        List<RefundRecoveryTarget> targets = refundRecoveryTargetReader.findTimedOutRequestTargets(
            LocalDateTime.now().plusMinutes(1),
            BATCH_SIZE
        );

        assertThat(targets).hasSize(BATCH_SIZE);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }
}
