package com.growmighty.lectures.firstday.refund.application;

import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import com.growmighty.lectures.firstday.refund.domain.Refund;
import com.growmighty.lectures.firstday.refund.domain.RefundReason;
import com.growmighty.lectures.firstday.refund.domain.RefundRepository;
import com.growmighty.lectures.firstday.refund.domain.RefundStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkRefundServiceTest {

    private static final Long REFUND_REQUEST_ID = 1L;
    private static final Long ORDER_ID = 2L;
    private static final Long PAYMENT_ID = 3L;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @InjectMocks
    private BulkRefundService bulkRefundService;

    @Test
    void PAID_결제를_사유와_함께_PLANNED_환불로_등록한다() {
        Payment payment = paidPayment();
        when(paymentRepository.findAllPaidByOrderIds(List.of(ORDER_ID))).thenReturn(List.of(payment));
        when(refundRepository.findExistingPaymentIds(List.of(PAYMENT_ID))).thenReturn(List.of());

        bulkRefundService.plan(REFUND_REQUEST_ID, List.of(ORDER_ID), RefundReason.GOAL_FAILED);

        ArgumentCaptor<Refund> captor = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(RefundStatus.PLANNED);
        assertThat(captor.getValue().getRefundRequestId()).isEqualTo(REFUND_REQUEST_ID);
        assertThat(captor.getValue().getReason()).isEqualTo(RefundReason.GOAL_FAILED);
    }

    @Test
    void 이미_환불_이력이_있는_결제는_등록하지_않는다() {
        Payment payment = paidPayment();
        when(paymentRepository.findAllPaidByOrderIds(List.of(ORDER_ID))).thenReturn(List.of(payment));
        when(refundRepository.findExistingPaymentIds(List.of(PAYMENT_ID))).thenReturn(List.of(PAYMENT_ID));

        bulkRefundService.plan(REFUND_REQUEST_ID, List.of(ORDER_ID), RefundReason.GOAL_FAILED);

        verify(refundRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void PAID_결제가_없으면_환불_이력을_조회하지_않는다() {
        when(paymentRepository.findAllPaidByOrderIds(List.of(ORDER_ID))).thenReturn(List.of());

        bulkRefundService.plan(REFUND_REQUEST_ID, List.of(ORDER_ID), RefundReason.GOAL_FAILED);

        verifyNoInteractions(refundRepository);
    }

    private Payment paidPayment() {
        Payment payment = Payment.ready(ORDER_ID, BigDecimal.valueOf(10_000));
        org.springframework.test.util.ReflectionTestUtils.setField(payment, "paymentId", PAYMENT_ID);
        payment.startConfirming("payment-key");
        payment.confirm("payment-key");
        return payment;
    }
}
