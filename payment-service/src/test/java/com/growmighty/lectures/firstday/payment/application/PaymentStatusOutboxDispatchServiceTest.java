package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.application.port.PaymentSingleResultEventPublisher;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatus;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutbox;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutboxRepository;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutboxStatus;
import com.growmighty.lectures.firstday.payment.infrastructure.kafka.dto.PaymentSingleResultEvent;
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
    private PaymentSingleResultEventPublisher paymentSingleResultEventPublisher;

    @InjectMocks
    private PaymentStatusOutboxDispatchService paymentStatusOutboxDispatchService;

    @Test
    void Kafka_발행에_성공하면_Outbox를_전송_완료로_저장한다() {
        PaymentStatusOutbox outbox = pendingOutbox();
        paymentStatusOutboxDispatchService.dispatch(outbox);

        verify(paymentSingleResultEventPublisher).publish(
            new PaymentSingleResultEvent(ORDER_ID, PaymentStatus.PAID.name()) // <--
        );
        verify(paymentStatusOutboxRepository).save(outbox);
        assertThat(outbox.getStatus()).isEqualTo(PaymentStatusOutboxStatus.SENT);
        assertThat(outbox.getSentAt()).isNotNull();
    }

    @Test
    void Kafka_발행에_실패하면_재시도_횟수를_증가시키고_예외를_전파한다() {
        PaymentStatusOutbox outbox = pendingOutbox();
        doThrow(new IllegalStateException("Kafka 발행 실패"))
            .when(paymentSingleResultEventPublisher)
            .publish(new PaymentSingleResultEvent(ORDER_ID, PaymentStatus.PAID.name())); // <--

        assertThatThrownBy(() -> paymentStatusOutboxDispatchService.dispatch(outbox))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Kafka 발행 실패");

        verify(paymentStatusOutboxRepository).save(outbox);
        assertThat(outbox.getStatus()).isEqualTo(PaymentStatusOutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
    }

    private PaymentStatusOutbox pendingOutbox() {
        return PaymentStatusOutbox.pending(PAYMENT_ID, ORDER_ID, PaymentStatus.PAID);
    }
}
