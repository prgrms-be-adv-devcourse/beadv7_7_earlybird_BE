package com.growmighty.lectures.firstday.payment.domain;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.payment.application.PaymentConfirmationService;
import com.growmighty.lectures.firstday.payment.application.PaymentGateway;
import com.growmighty.lectures.firstday.payment.application.PaymentService;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentInfo;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
class PaymentTest {

    private static final Long ORDER_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final String PG_ORDER_ID = "order_123";
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(10_000);

    private InMemoryPaymentRepository paymentRepository;
    private RecordingPaymentGateway paymentGateway;
    private PaymentConfirmationService paymentConfirmationService;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentRepository = new InMemoryPaymentRepository();
        paymentGateway = new RecordingPaymentGateway();
        paymentConfirmationService = new PaymentConfirmationService(paymentRepository);
        paymentService = new PaymentService(
            paymentRepository,
            paymentGateway,
            paymentConfirmationService
        );
    }

    @Test
    @DisplayName("0 이하 금액으로는 결제를 생성할 수 없다")
    void ready_invalidAmount_throws() {
        assertThatThrownBy(() -> Payment.ready(ORDER_ID, PG_ORDER_ID, USER_ID, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("주문 식별자 없이는 결제를 생성할 수 없다")
    void ready_withoutOrderId_throws() {
        assertThatThrownBy(() -> Payment.ready(null, PG_ORDER_ID, USER_ID, AMOUNT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("사용자 식별자 없이는 결제를 생성할 수 없다")
    void ready_withoutUserId_throws() {
        assertThatThrownBy(() -> Payment.ready(ORDER_ID, PG_ORDER_ID, null, AMOUNT))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("READY 결제를 만들면 승인 재시도용 멱등키가 생성된다")
    void ready_generatesApproveIdempotencyKey() {
        Payment payment = readyPayment();

        assertThat(payment.getApproveIdempotencyKey()).isNotBlank();
    }

    @Test
    @DisplayName("READY 결제는 승인 처리를 시작하면 CONFIRMING으로 전이된다")
    void startConfirming_transitions() {
        Payment payment = readyPayment();

        payment.startConfirming();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CONFIRMING);
        log.info("payment state changed: orderId={}, status={}", ORDER_ID, payment.getStatus());
    }

    @Test
    @DisplayName("CONFIRMING 상태에서 승인하면 PAID로 전이되고 paymentKey가 저장된다")
    void confirm_transitions() {
        Payment payment = readyPayment();
        payment.startConfirming();

        payment.confirm("payment-key-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaymentKey()).isEqualTo("payment-key-1");
        assertThat(payment.isPaid()).isTrue();
        log.info("payment approved: orderId={}, pgOrderId={}, paymentKey={}, amount={}, status={}",
                ORDER_ID, PG_ORDER_ID, payment.getPaymentKey(), payment.getAmount(), payment.getStatus());
    }

    @Test
    @DisplayName("READY 상태에서는 바로 승인할 수 없다")
    void confirm_fromReady_throws() {
        Payment payment = readyPayment();

        assertThatThrownBy(() -> payment.confirm("payment-key-1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("이미 승인된 결제를 다시 승인하면 예외가 발생한다")
    void confirm_twice_throws() {
        Payment payment = readyPayment();
        payment.startConfirming();
        payment.confirm("payment-key-1");

        assertThatThrownBy(() -> payment.confirm("payment-key-1"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("CONFIRMING 상태에서 실패 처리하면 FAILED로 전이된다")
    void fail_transitions() {
        Payment payment = readyPayment();
        payment.startConfirming();

        payment.fail();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        log.info("payment approval failed: orderId={}, status={}", ORDER_ID, payment.getStatus());
    }

    @Test
    @DisplayName("prepare는 서버가 전달받은 주문 정보를 READY 결제로 저장한다")
    void prepare_savesReadyPayment() {
        PaymentInfo result = paymentService.prepare(ORDER_ID, PG_ORDER_ID, USER_ID, AMOUNT);

        Payment saved = paymentRepository.findByPgOrderId(PG_ORDER_ID).orElseThrow();
        assertThat(result.status()).isEqualTo(PaymentStatus.READY);
        assertThat(saved.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getAmount()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    @DisplayName("동일한 주문 정보로 prepare를 재요청하면 기존 READY 결제를 반환한다")
    void prepare_withSameRequest_returnsExistingPayment() {
        paymentService.prepare(ORDER_ID, PG_ORDER_ID, USER_ID, AMOUNT);

        PaymentInfo result = paymentService.prepare(ORDER_ID, PG_ORDER_ID, USER_ID, AMOUNT);

        assertThat(result.status()).isEqualTo(PaymentStatus.READY);
        assertThat(paymentRepository.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 주문에 다른 금액으로 prepare를 재요청하면 실패한다")
    void prepare_withDifferentAmount_throws() {
        paymentService.prepare(ORDER_ID, PG_ORDER_ID, USER_ID, AMOUNT);

        assertThatThrownBy(() -> paymentService.prepare(
                ORDER_ID, PG_ORDER_ID, USER_ID, BigDecimal.valueOf(9_999)
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("confirm은 prepare에 저장된 금액으로 Fake PG를 승인하고 PAID 처리한다")
    void confirm_approvesUsingPreparedAmount() {
        paymentService.prepare(ORDER_ID, PG_ORDER_ID, USER_ID, AMOUNT);

        PaymentInfo result = paymentService.confirm("payment-key-1", PG_ORDER_ID);

        assertThat(result.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(paymentGateway.approvalCalls).isEqualTo(1);
        assertThat(paymentGateway.requestedPaymentKey).isEqualTo("payment-key-1");
        assertThat(paymentGateway.requestedPgOrderId).isEqualTo(PG_ORDER_ID);
        assertThat(paymentGateway.requestedAmount).isEqualByComparingTo(AMOUNT);
        assertThat(paymentGateway.requestedIdempotencyKey)
            .isEqualTo(paymentRepository.findByPgOrderId(PG_ORDER_ID).orElseThrow().getApproveIdempotencyKey());
        assertThat(paymentRepository.findByPgOrderId(PG_ORDER_ID).orElseThrow().isPaid()).isTrue();
    }

    @Test
    @DisplayName("이미 PAID인 결제를 다시 confirm하면 PG를 다시 호출하지 않고 실패한다")
    void confirm_whenAlreadyPaid_doesNotCallGatewayAgain() {
        paymentService.prepare(ORDER_ID, PG_ORDER_ID, USER_ID, AMOUNT);
        paymentService.confirm("payment-key-1", PG_ORDER_ID);

        assertThatThrownBy(() -> paymentService.confirm("payment-key-1", PG_ORDER_ID))
            .isInstanceOf(IllegalStateException.class);
        assertThat(paymentGateway.approvalCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("prepare되지 않은 pgOrderId로 confirm하면 실패한다")
    void confirm_withoutPreparedPayment_throws() {
        assertThatThrownBy(() -> paymentService.confirm("payment-key-1", "unknown-order"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private Payment readyPayment() {
        Payment payment = Payment.ready(ORDER_ID, PG_ORDER_ID, USER_ID, AMOUNT);
        log.info("payment prepared: orderId={}, pgOrderId={}, userId={}, amount={}, status={}",
                ORDER_ID, PG_ORDER_ID, USER_ID, AMOUNT, payment.getStatus());
        return payment;
    }

    private static final class RecordingPaymentGateway implements PaymentGateway {
        private int approvalCalls;
        private String requestedPaymentKey;
        private String requestedPgOrderId;
        private BigDecimal requestedAmount;
        private String requestedIdempotencyKey;

        @Override
        public PgApproval approve(String paymentKey, String pgOrderId, BigDecimal amount, String idempotencyKey) {
            approvalCalls++;
            requestedPaymentKey = paymentKey;
            requestedPgOrderId = pgOrderId;
            requestedAmount = amount;
            requestedIdempotencyKey = idempotencyKey;
            return new PgApproval(paymentKey, pgOrderId, amount);
        }

        @Override
        public void cancel(String paymentKey) {
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
