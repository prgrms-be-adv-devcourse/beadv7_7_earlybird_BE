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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkRefundServiceTest {

    private static final Long SETTLEMENT_ID = 1L;
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
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(refundRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());

        bulkRefundService.plan(SETTLEMENT_ID, List.of(ORDER_ID), RefundReason.GOAL_FAILED);

        ArgumentCaptor<Refund> captor = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(RefundStatus.PLANNED);
        assertThat(captor.getValue().getSettlementId()).isEqualTo(SETTLEMENT_ID);
        assertThat(captor.getValue().getReason()).isEqualTo(RefundReason.GOAL_FAILED);
    }

    private Payment paidPayment() {
        Payment payment = Payment.ready(ORDER_ID, BigDecimal.valueOf(10_000));
        org.springframework.test.util.ReflectionTestUtils.setField(payment, "paymentId", PAYMENT_ID);
        payment.startConfirming("payment-key");
        payment.confirm("payment-key");
        return payment;
    }
}
