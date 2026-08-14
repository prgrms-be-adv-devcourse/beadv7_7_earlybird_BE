package com.growmighty.lectures.firstday.refund.application;

import com.growmighty.lectures.firstday.refund.application.dto.RefundCancellationTarget;
import com.growmighty.lectures.firstday.refund.application.exception.RefundGatewayException;
import com.growmighty.lectures.firstday.refund.application.exception.RefundGatewayFailureType;
import com.growmighty.lectures.firstday.refund.application.port.RefundGateway;
import com.growmighty.lectures.firstday.refund.domain.RefundReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundCancellationSagaOrchestratorTest {

    private static final Long ORDER_ID = 1L;
    private static final Long REFUND_ID = 1L;
    private static final String PAYMENT_KEY = "payment-key";
    private static final String CANCEL_IDEMPOTENCY_KEY = "cancel-idempotency-key";

    @Mock
    private RefundService refundService;

    @Mock
    private RefundGateway refundGateway;

    @InjectMocks
    private RefundCancellationSagaOrchestrator orchestrator;

    @Test
    void cancel_completesRefundAfterTossRefundSucceeds() {
        RefundCancellationTarget target = target();
        when(refundService.startRefund(ORDER_ID, RefundReason.USER_CANCEL)).thenReturn(target);

        orchestrator.cancel(ORDER_ID, RefundReason.USER_CANCEL);

        var inOrder = inOrder(refundService, refundGateway);
        inOrder.verify(refundService).startRefund(ORDER_ID, RefundReason.USER_CANCEL);
        inOrder.verify(refundGateway).refund(PAYMENT_KEY, RefundReason.USER_CANCEL, CANCEL_IDEMPOTENCY_KEY);
        inOrder.verify(refundService).completeRefund(REFUND_ID);
    }

    @Test
    void cancel_marksRefundFailedWhenTossRefundFailsDefinitely() {
        RefundCancellationTarget target = target();
        RefundGatewayException exception = gatewayException(RefundGatewayFailureType.DEFINITIVE);
        when(refundService.startRefund(ORDER_ID, RefundReason.USER_CANCEL)).thenReturn(target);
        doThrow(exception).when(refundGateway)
            .refund(PAYMENT_KEY, RefundReason.USER_CANCEL, CANCEL_IDEMPOTENCY_KEY);

        assertThatThrownBy(() -> orchestrator.cancel(ORDER_ID, RefundReason.USER_CANCEL))
            .isSameAs(exception);

        verify(refundService).failRefund(REFUND_ID);
        verify(refundService, never()).completeRefund(REFUND_ID);
    }

    @Test
    void cancel_schedulesRefundRetryWhenTossRefundResultIsUncertain() {
        RefundCancellationTarget target = target();
        RefundGatewayException exception = gatewayException(RefundGatewayFailureType.UNCERTAIN);
        when(refundService.startRefund(ORDER_ID, RefundReason.USER_CANCEL)).thenReturn(target);
        doThrow(exception).when(refundGateway)
            .refund(PAYMENT_KEY, RefundReason.USER_CANCEL, CANCEL_IDEMPOTENCY_KEY);

        assertThatThrownBy(() -> orchestrator.cancel(ORDER_ID, RefundReason.USER_CANCEL))
            .isSameAs(exception);

        verify(refundService).scheduleRetry(REFUND_ID);
        verify(refundService, never()).failRefund(REFUND_ID);
        verify(refundService, never()).completeRefund(REFUND_ID);
    }

    @Test
    void cancelPlannedRefund_completesRefundAfterTossRefundSucceeds() {
        RefundCancellationTarget target = target();
        when(refundService.startPlannedRefund(REFUND_ID)).thenReturn(target);

        orchestrator.cancelPlannedRefund(REFUND_ID);

        var inOrder = inOrder(refundService, refundGateway);
        inOrder.verify(refundService).startPlannedRefund(REFUND_ID);
        inOrder.verify(refundGateway).refund(PAYMENT_KEY, RefundReason.USER_CANCEL, CANCEL_IDEMPOTENCY_KEY);
        inOrder.verify(refundService).completeRefund(REFUND_ID);
    }

    private RefundCancellationTarget target() {
        return new RefundCancellationTarget(
            REFUND_ID,
            PAYMENT_KEY,
            RefundReason.USER_CANCEL,
            CANCEL_IDEMPOTENCY_KEY
        );
    }

    private RefundGatewayException gatewayException(RefundGatewayFailureType failureType) {
        return new RefundGatewayException(
            HttpStatus.SERVICE_UNAVAILABLE,
            failureType,
            "Toss refund failed"
        );
    }
}
