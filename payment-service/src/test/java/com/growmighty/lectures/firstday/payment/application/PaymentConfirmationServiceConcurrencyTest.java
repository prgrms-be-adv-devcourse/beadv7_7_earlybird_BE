package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.config.JpaAuditingConfig;
import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatus;
import com.growmighty.lectures.firstday.payment.infrastructure.PaymentRepositoryAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create",
    "spring.cloud.config.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    JpaAuditingConfig.class,
    PaymentRepositoryAdapter.class,
    PaymentConfirmationService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentConfirmationServiceConcurrencyTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private PaymentConfirmationService paymentConfirmationService;

    @Autowired
    private PaymentRepository paymentRepository;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    @DisplayName("동시에 승인 선점을 요청해도 하나의 요청만 CONFIRMING 상태로 전이시킨다")
    void startConfirmation_allowsOnlyOneRequest() throws Exception {
        Payment payment = paymentRepository.save(Payment.ready(
            1L,
            BigDecimal.valueOf(10_000)
        ));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> request = () -> {
            ready.countDown();
            start.await();

            try {
                paymentConfirmationService.startConfirmation(payment.getPgOrderId(), payment.getAmount());
                return true;
            } catch (RuntimeException ignored) {
                return false;
            }
        };

        Future<Boolean> first = executorService.submit(request);
        Future<Boolean> second = executorService.submit(request);

        ready.await();
        start.countDown();

        List<Boolean> results = List.of(first.get(), second.get());

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(paymentRepository.findById(payment.getPaymentId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.CONFIRMING);
    }
}
