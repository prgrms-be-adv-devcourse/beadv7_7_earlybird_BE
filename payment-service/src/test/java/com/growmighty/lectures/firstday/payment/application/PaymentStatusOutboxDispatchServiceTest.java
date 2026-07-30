package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.application.port.OrderStatusPort;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatus;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutbox;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutboxRepository;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutboxStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentStatusOutboxDispatchServiceTest {

    private static final Long PAYMENT_ID = 1L;
    private static final Long ORDER_ID = 2L;

    @Mock
    private PaymentStatusOutboxRepository paymentStatusOutboxRepository;

    @Mock
    private OrderStatusPort orderStatusPort;

    @InjectMocks
    private PaymentStatusOutboxDispatchService paymentStatusOutboxDispatchService;

    @Test
    void 주문_상태_통지에_성공하면_Outbox를_전송_완료로_저장한다() {
        PaymentStatusOutbox outbox = pendingOutbox();

        paymentStatusOutboxDispatchService.dispatch(outbox);

        verify(orderStatusPort).notifyStatus(ORDER_ID, PaymentStatus.PAID);
        verify(paymentStatusOutboxRepository).save(outbox);
        assertThat(outbox.getStatus()).isEqualTo(PaymentStatusOutboxStatus.SENT);
        assertThat(outbox.getSentAt()).isNotNull();
    }

    @Test
    void 주문_상태_통지에_실패하면_재시도_횟수를_증가시키고_예외를_전파한다() {
        PaymentStatusOutbox outbox = pendingOutbox();
        doThrow(new IllegalStateException("Order 통지 실패"))
            .when(orderStatusPort)
            .notifyStatus(ORDER_ID, PaymentStatus.PAID);

        assertThatThrownBy(() -> paymentStatusOutboxDispatchService.dispatch(outbox))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Order 통지 실패");

        verify(paymentStatusOutboxRepository).save(outbox);
        assertThat(outbox.getStatus()).isEqualTo(PaymentStatusOutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
    }

    private PaymentStatusOutbox pendingOutbox() {
        return PaymentStatusOutbox.pending(PAYMENT_ID, ORDER_ID, PaymentStatus.PAID);
    }
}
