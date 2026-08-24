package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.application.dto.PaymentConfirmationTarget;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentInfo;
import com.growmighty.lectures.firstday.payment.application.exception.PaymentGatewayException;
import com.growmighty.lectures.firstday.payment.application.exception.PaymentGatewayFailureType;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentApprovalSagaServiceTest {

    private static final Long PAYMENT_ID = 1L;
    private static final Long ORDER_ID = 2L;
    private static final String PAYMENT_KEY = "payment-key";
    private static final String PG_ORDER_ID = "order-2";
    private static final String IDEMPOTENCY_KEY = "idempotency-key";
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(10_000);

    @Mock
    private PaymentConfirmationService paymentConfirmationService;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private PaymentApprovalSagaOrchestrator paymentApprovalSagaService;

    // 추가 : PG 승인 성공 시 PAID 완료 처리를 검증
    @Test
    void 승인_성공_시_PAID로_완료한다() {
        PaymentConfirmationTarget target = confirmationTarget();
        PaymentGateway.PgApproval approval = new PaymentGateway.PgApproval(
            PAYMENT_KEY,
            PG_ORDER_ID,
            AMOUNT
        );
        PaymentInfo paidPayment = new PaymentInfo(
            PAYMENT_ID,
            ORDER_ID,
            PG_ORDER_ID, // <--
            AMOUNT,
            PaymentStatus.PAID
        );
        when(paymentConfirmationService.startConfirmation(PAYMENT_KEY, PG_ORDER_ID, AMOUNT))
            .thenReturn(target);
        when(paymentGateway.approve(PAYMENT_KEY, PG_ORDER_ID, AMOUNT, IDEMPOTENCY_KEY))
            .thenReturn(approval);
        when(paymentConfirmationService.completeConfirmation(PAYMENT_ID, PAYMENT_KEY, approval))
            .thenReturn(paidPayment);

        PaymentInfo result = paymentApprovalSagaService.approve(PAYMENT_KEY, PG_ORDER_ID, AMOUNT);

        assertThat(result.status()).isEqualTo(PaymentStatus.PAID);
        verify(paymentConfirmationService).completeConfirmation(PAYMENT_ID, PAYMENT_KEY, approval);
    }

    // 추가 : 확정적인 PG 승인 실패 시 FAILED 기록을 검증
    @Test
    void 확정_실패_시_FAILED로_기록한다() {
        PaymentConfirmationTarget target = confirmationTarget();
        PaymentGatewayException exception = gatewayException(PaymentGatewayFailureType.DEFINITIVE);
        when(paymentConfirmationService.startConfirmation(PAYMENT_KEY, PG_ORDER_ID, AMOUNT))
            .thenReturn(target);
        when(paymentGateway.approve(PAYMENT_KEY, PG_ORDER_ID, AMOUNT, IDEMPOTENCY_KEY))
            .thenThrow(exception);

        assertThatThrownBy(() -> paymentApprovalSagaService.approve(PAYMENT_KEY, PG_ORDER_ID, AMOUNT))
            .isSameAs(exception);

        verify(paymentConfirmationService).failConfirmation(PAYMENT_ID);
    }

    // 추가 : 결과 미확정 PG 오류 시 CONFIRMING 유지를 검증
    @Test
    void 결과_미확정_실패_시_FAILED로_변경하지_않는다() {
        PaymentConfirmationTarget target = confirmationTarget();
        PaymentGatewayException exception = gatewayException(PaymentGatewayFailureType.UNCERTAIN);
        when(paymentConfirmationService.startConfirmation(PAYMENT_KEY, PG_ORDER_ID, AMOUNT))
            .thenReturn(target);
        when(paymentGateway.approve(PAYMENT_KEY, PG_ORDER_ID, AMOUNT, IDEMPOTENCY_KEY))
            .thenThrow(exception);

        assertThatThrownBy(() -> paymentApprovalSagaService.approve(PAYMENT_KEY, PG_ORDER_ID, AMOUNT))
            .isSameAs(exception);

        verify(paymentConfirmationService, never()).failConfirmation(PAYMENT_ID);
    }

    @Test
    void 승인_완료_경합에서_다른_경로가_PAID로_확정했으면_PAID를_반환한다() {
        PaymentConfirmationTarget target = confirmationTarget();
        PaymentGateway.PgApproval approval = new PaymentGateway.PgApproval(PAYMENT_KEY, PG_ORDER_ID, AMOUNT);
        PaymentInfo paidPayment = paidPayment();
        when(paymentConfirmationService.startConfirmation(PAYMENT_KEY, PG_ORDER_ID, AMOUNT))
            .thenReturn(target);
        when(paymentGateway.approve(PAYMENT_KEY, PG_ORDER_ID, AMOUNT, IDEMPOTENCY_KEY))
            .thenReturn(approval);
        doThrow(new OptimisticLockingFailureException("결제 상태가 변경되었습니다."))
            .when(paymentConfirmationService).completeConfirmation(PAYMENT_ID, PAYMENT_KEY, approval);
        when(paymentConfirmationService.findPaidPaymentInfo(PAYMENT_ID))
            .thenReturn(Optional.of(paidPayment));

        PaymentInfo result = paymentApprovalSagaService.approve(PAYMENT_KEY, PG_ORDER_ID, AMOUNT);

        assertThat(result).isEqualTo(paidPayment);
    }

    @Test
    void 승인_완료_경합에서_PAID가_아니면_낙관적_락_예외를_전파한다() {
        PaymentConfirmationTarget target = confirmationTarget();
        PaymentGateway.PgApproval approval = new PaymentGateway.PgApproval(PAYMENT_KEY, PG_ORDER_ID, AMOUNT);
        OptimisticLockingFailureException exception = new OptimisticLockingFailureException("결제 상태가 변경되었습니다.");
        when(paymentConfirmationService.startConfirmation(PAYMENT_KEY, PG_ORDER_ID, AMOUNT))
            .thenReturn(target);
        when(paymentGateway.approve(PAYMENT_KEY, PG_ORDER_ID, AMOUNT, IDEMPOTENCY_KEY))
            .thenReturn(approval);
        doThrow(exception).when(paymentConfirmationService)
            .completeConfirmation(PAYMENT_ID, PAYMENT_KEY, approval);
        when(paymentConfirmationService.findPaidPaymentInfo(PAYMENT_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentApprovalSagaService.approve(PAYMENT_KEY, PG_ORDER_ID, AMOUNT))
            .isSameAs(exception);
    }

    // 추가 : 승인 완료 결과 DTO 생성
    private PaymentInfo paidPayment() {
        return new PaymentInfo(PAYMENT_ID, ORDER_ID, PG_ORDER_ID, AMOUNT, PaymentStatus.PAID);
    }

    private PaymentConfirmationTarget confirmationTarget() {
        return new PaymentConfirmationTarget(PAYMENT_ID, PG_ORDER_ID, AMOUNT, IDEMPOTENCY_KEY);
    }

    private PaymentGatewayException gatewayException(PaymentGatewayFailureType failureType) {
        return new PaymentGatewayException(HttpStatus.CONFLICT, failureType, "PG 승인 실패");
    }
}
