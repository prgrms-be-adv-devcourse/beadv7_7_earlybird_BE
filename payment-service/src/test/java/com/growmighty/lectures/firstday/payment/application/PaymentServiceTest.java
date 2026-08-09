package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentInfo;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentPreparationInfo;
import com.growmighty.lectures.firstday.payment.application.exception.PaymentConfirmationInProgressException;
import com.growmighty.lectures.firstday.payment.config.PaymentRecoveryProperties;
import com.growmighty.lectures.firstday.payment.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentServiceTest {

    private static final Long ORDER_ID = 1L;
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(10_000);

    private InMemoryPaymentRepository paymentRepository;
    private InMemoryPaymentStatusOutboxRepository paymentStatusOutboxRepository;
    private RecordingPaymentGateway paymentGateway;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentRepository = new InMemoryPaymentRepository();
        paymentStatusOutboxRepository = new InMemoryPaymentStatusOutboxRepository();
        paymentGateway = new RecordingPaymentGateway();
        PaymentConfirmationService paymentConfirmationService = new PaymentConfirmationService( // <-- SAGA 상태 전이 의존성 구성
            paymentRepository,
            paymentStatusOutboxRepository,
            new PaymentRecoveryProperties(Duration.ofMinutes(3), 100, Duration.ofMinutes(10))
        );
        paymentService = new PaymentService(
            paymentRepository,
            paymentGateway,
            new PaymentApprovalSagaOrchestrator(paymentConfirmationService, paymentGateway) // <-- 승인 SAGA 주입
        );
    }

    @Test
    @DisplayName("prepare는 서버가 전달받은 주문 정보를 READY 결제로 저장하고 PG 주문번호를 생성한다")
    void prepare_savesReadyPayment() {
        PaymentPreparationInfo result = paymentService.prepare(ORDER_ID, AMOUNT);

        Payment saved = paymentRepository.findByOrderId(ORDER_ID).orElseThrow();

        assertThat(result.status()).isEqualTo(PaymentStatus.READY);
        assertThat(result.pgOrderId()).isEqualTo(saved.getPgOrderId());
        assertThat(result.pgOrderId()).startsWith("order-" + ORDER_ID + "-");
        assertThat(saved.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(saved.getAmount()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    @DisplayName("동일한 주문 정보로 prepare를 재요청하면 기존 READY 결제를 반환한다")
    void prepare_withSameRequest_returnsExistingPayment() {
        PaymentPreparationInfo first = paymentService.prepare(ORDER_ID, AMOUNT);

        PaymentPreparationInfo result = paymentService.prepare(ORDER_ID, AMOUNT);

        assertThat(result.status()).isEqualTo(PaymentStatus.READY);
        assertThat(result.paymentId()).isEqualTo(first.paymentId());
        assertThat(result.pgOrderId()).isEqualTo(first.pgOrderId());
        assertThat(paymentRepository.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 주문에 다른 금액으로 prepare를 재요청하면 실패한다")
    void prepare_withDifferentAmount_throws() {
        paymentService.prepare(ORDER_ID, AMOUNT);

        assertThatThrownBy(() -> paymentService.prepare(ORDER_ID, BigDecimal.valueOf(9_999)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("CONFIRMING 결제는 prepare를 재요청할 수 없다")
    void prepare_whenConfirming_throws() {
        PaymentPreparationInfo prepared = paymentService.prepare(ORDER_ID, AMOUNT);
        Payment payment = paymentRepository.findById(prepared.paymentId()).orElseThrow();
        payment.startConfirming("payment-key-1");

        assertThatThrownBy(() -> paymentService.prepare(ORDER_ID, AMOUNT))
            .isInstanceOf(PaymentConfirmationInProgressException.class);
    }

    @Test
    @DisplayName("PAID 결제는 prepare를 재요청할 수 없다")
    void prepare_whenPaid_throws() {
        PaymentPreparationInfo prepared = paymentService.prepare(ORDER_ID, AMOUNT);
        paymentService.confirm("payment-key-1", prepared.pgOrderId(), AMOUNT);

        assertThatThrownBy(() -> paymentService.prepare(ORDER_ID, AMOUNT))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("이미 결제가 완료된 주문입니다.");
    }

    @Test
    @DisplayName("FAILED 결제는 재결제 처리 전까지 prepare를 재요청할 수 없다")
    void prepare_whenFailed_throws() {
        PaymentPreparationInfo prepared = paymentService.prepare(ORDER_ID, AMOUNT);
        Payment payment = paymentRepository.findById(prepared.paymentId()).orElseThrow();
        payment.startConfirming("payment-key-1");
        payment.fail();

        assertThatThrownBy(() -> paymentService.prepare(ORDER_ID, AMOUNT))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("재결제 처리가 필요합니다.");
    }

    @Test
    @DisplayName("CANCELLED 결제는 prepare를 재요청할 수 없다")
    void prepare_whenCancelled_throws() {
        PaymentPreparationInfo prepared = paymentService.prepare(ORDER_ID, AMOUNT);
        paymentService.confirm("payment-key-1", prepared.pgOrderId(), AMOUNT);
        Payment payment = paymentRepository.findById(prepared.paymentId()).orElseThrow();
        payment.cancel();

        assertThatThrownBy(() -> paymentService.prepare(ORDER_ID, AMOUNT))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("취소된 결제입니다.");
    }

    @Test
    @DisplayName("confirm은 prepare에 저장된 금액과 멱등키로 승인하고 PAID 처리한다")
    void confirm_approvesUsingPreparedPayment() {
        PaymentPreparationInfo prepared = paymentService.prepare(ORDER_ID, AMOUNT);

        PaymentInfo result = paymentService.confirm("payment-key-1", prepared.pgOrderId(), AMOUNT);
        Payment saved = paymentRepository.findByPgOrderId(prepared.pgOrderId()).orElseThrow();

        assertThat(result.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(saved.isPaid()).isTrue();
        assertThat(paymentGateway.approvalCalls).isEqualTo(1);
        assertThat(paymentGateway.requestedPaymentKey).isEqualTo("payment-key-1");
        assertThat(paymentGateway.requestedPgOrderId).isEqualTo(prepared.pgOrderId());
        assertThat(paymentGateway.requestedAmount).isEqualByComparingTo(AMOUNT);
        assertThat(paymentGateway.requestedIdempotencyKey)
            .isEqualTo(saved.getApproveIdempotencyKey());
    }

    @Test
    @DisplayName("PG 승인 응답 paymentKey가 요청 paymentKey와 다르면 결제를 완료하지 않는다")
    void confirm_withDifferentResponsePaymentKey_throws() {
        PaymentPreparationInfo prepared = paymentService.prepare(ORDER_ID, AMOUNT);
        paymentGateway.approvalToReturn = new PaymentGateway.PgApproval(
            "different-payment-key",
            prepared.pgOrderId(),
            AMOUNT
        );

        assertThatThrownBy(() -> paymentService.confirm(
            "payment-key-1", prepared.pgOrderId(), AMOUNT
        )).isInstanceOf(IllegalStateException.class)
            .hasMessage("PG paymentKey가 일치하지 않습니다.");

        Payment saved = paymentRepository.findByPgOrderId(prepared.pgOrderId()).orElseThrow();

        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.CONFIRMING);
        assertThat(saved.getPaymentKey()).isEqualTo("payment-key-1");
    }

    @Test
    @DisplayName("이미 PAID인 결제를 다시 confirm하면 PG를 다시 호출하지 않는다")
    void confirm_whenAlreadyPaid_doesNotCallGatewayAgain() {
        PaymentPreparationInfo prepared = paymentService.prepare(ORDER_ID, AMOUNT);
        paymentService.confirm("payment-key-1", prepared.pgOrderId(), AMOUNT);

        assertThatThrownBy(() -> paymentService.confirm(
            "payment-key-1", prepared.pgOrderId(), AMOUNT
        )).isInstanceOf(IllegalStateException.class);

        assertThat(paymentGateway.approvalCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("prepare되지 않은 pgOrderId로 confirm하면 실패한다")
    void confirm_withoutPreparedPayment_throws() {
        assertThatThrownBy(() -> paymentService.confirm(
            "payment-key-1", "unknown-order", AMOUNT
        )).isInstanceOf(EntityNotFoundException.class);
    }

    private static final class RecordingPaymentGateway implements PaymentGateway {
        private int approvalCalls;
        private String requestedPaymentKey;
        private String requestedPgOrderId;
        private BigDecimal requestedAmount;
        private String requestedIdempotencyKey;
        private PgApproval approvalToReturn;

        @Override
        public PgApproval approve(
            String paymentKey,
            String pgOrderId,
            BigDecimal amount,
            String idempotencyKey
        ) {
            approvalCalls++;
            requestedPaymentKey = paymentKey;
            requestedPgOrderId = pgOrderId;
            requestedAmount = amount;
            requestedIdempotencyKey = idempotencyKey;

            return approvalToReturn != null
                ? approvalToReturn
                : new PgApproval(paymentKey, pgOrderId, amount);
        }

        @Override
        public PgPayment getPayment(String paymentKey) {
            throw new UnsupportedOperationException("이 테스트에서는 결제 조회를 사용하지 않습니다.");
        }

        @Override
        public void cancel(String paymentKey) {
        }
    }

    private static final class InMemoryPaymentStatusOutboxRepository
        implements PaymentStatusOutboxRepository {

        private final List<PaymentStatusOutbox> outboxes = new ArrayList<>();

        @Override
        public PaymentStatusOutbox save(PaymentStatusOutbox outbox) {
            outboxes.add(outbox);
            return outbox;
        }

        @Override
        public boolean existsByPaymentIdAndPaymentStatus(Long paymentId, PaymentStatus paymentStatus) {
            return outboxes.stream().anyMatch(outbox ->
                outbox.getPaymentId().equals(paymentId)
                    && outbox.getPaymentStatus() == paymentStatus
            );
        }

        @Override
        public List<PaymentStatusOutbox> findPending(int limit) {
            return outboxes.stream()
                .filter(PaymentStatusOutbox::isPending)
                .limit(limit)
                .toList();
        }
    }

    private static final class InMemoryPaymentRepository implements PaymentRepository {
        private final AtomicLong sequence = new AtomicLong();
        private final Map<Long, Payment> paymentsById = new HashMap<>();
        private final Map<Long, Payment> paymentsByOrderId = new HashMap<>();
        private final Map<String, Payment> paymentsByPgOrderId = new HashMap<>();

        @Override
        public Payment save(Payment payment) {
            if (payment.getPaymentId() == null) {
                assignPaymentId(payment, sequence.incrementAndGet());
            }

            paymentsById.put(payment.getPaymentId(), payment);
            paymentsByOrderId.put(payment.getOrderId(), payment);
            paymentsByPgOrderId.put(payment.getPgOrderId(), payment);
            return payment;
        }

        @Override
        public Optional<Payment> findById(Long id) {
            return Optional.ofNullable(paymentsById.get(id));
        }

        @Override
        public Optional<Payment> findByOrderId(Long orderId) {
            return Optional.ofNullable(paymentsByOrderId.get(orderId));
        }

        @Override
        public Optional<Payment> findByPgOrderId(String pgOrderId) {
            return Optional.ofNullable(paymentsByPgOrderId.get(pgOrderId));
        }

        @Override
        public List<Long> findConfirmingPaymentIdsBefore(LocalDateTime cutoff, int limit) {
            return paymentsById.values().stream()
                .filter(Payment::isConfirming)
                .filter(payment -> payment.getConfirmingAt().isBefore(cutoff))
                .sorted(Comparator.comparing(Payment::getConfirmingAt))
                .limit(limit)
                .map(Payment::getPaymentId)
                .toList();
        }

        private int size() {
            return paymentsByOrderId.size();
        }

        private void assignPaymentId(Payment payment, Long paymentId) {
            try {
                Field field = Payment.class.getDeclaredField("paymentId");
                field.setAccessible(true);
                field.set(payment, paymentId);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("테스트 결제 ID를 설정할 수 없습니다.", e);
            }
        }
    }
}
