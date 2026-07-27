package com.growmighty.lectures.firstday.refund.application;

import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatus;
import com.growmighty.lectures.firstday.refund.application.port.RefundGateway;
import com.growmighty.lectures.firstday.refund.domain.Refund;
import com.growmighty.lectures.firstday.refund.domain.RefundReason;
import com.growmighty.lectures.firstday.refund.domain.RefundRepository;
import com.growmighty.lectures.firstday.refund.domain.RefundStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    private static final Long PAYMENT_ID = 1L;
    private static final UUID ORDER_ID =
        UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(10_000);
    private static final String PAYMENT_KEY = "payment-key";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private RefundGateway refundGateway;

    @InjectMocks
    private RefundService refundService;

    @Test
    void paidPayment_refund_completesRefundAndCancelsPayment() {
        Payment payment = paidPayment();
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(refundRepository.save(any(Refund.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Refund result = refundService.refund(ORDER_ID, RefundReason.USER_CANCEL);

        assertThat(result.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(result.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(result.getAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);

        verify(refundGateway).refund(PAYMENT_KEY, RefundReason.USER_CANCEL);
        verify(paymentRepository).save(payment);
        verify(refundRepository, times(2)).save(any(Refund.class));
    }

    @Test
    void notPaidPayment_refund_throws() {
        Payment payment = Payment.ready(ORDER_ID, AMOUNT);
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> refundService.refund(ORDER_ID, RefundReason.USER_CANCEL))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PAID 상태의 결제만 환불할 수 있습니다.");

        verifyNoInteractions(refundRepository, refundGateway);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void refundGatewayFails_refund_keepsPaymentPaid() {
        Payment payment = paidPayment();
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(refundRepository.save(any(Refund.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("Toss 환불 실패"))
            .when(refundGateway)
            .refund(PAYMENT_KEY, RefundReason.USER_CANCEL);

        assertThatThrownBy(() -> refundService.refund(ORDER_ID, RefundReason.USER_CANCEL))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Toss 환불 실패");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentRepository, never()).save(payment);
    }

    private Payment paidPayment() {
        Payment payment = Payment.ready(ORDER_ID, AMOUNT);
        ReflectionTestUtils.setField(payment, "paymentId", PAYMENT_ID);
        payment.startConfirming(PAYMENT_KEY);
        payment.confirm(PAYMENT_KEY);
        return payment;
    }
}
