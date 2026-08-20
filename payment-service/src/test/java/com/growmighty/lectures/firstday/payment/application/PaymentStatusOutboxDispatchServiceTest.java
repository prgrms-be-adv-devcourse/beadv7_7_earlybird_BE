package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.application.dto.PaymentStatusChangedEvent;
import com.growmighty.lectures.firstday.payment.application.port.PaymentStatusChangedEventPublisher;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatus;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutbox;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutboxRepository;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentStatusOutboxDispatchServiceTest {

    private static final Long PAYMENT_ID = 1L;
    private static final Long ORDER_ID = 2L;
    private static final String PG_ORDER_ID = "earlybird-2";

    @Mock
    private PaymentStatusOutboxRepository paymentStatusOutboxRepository;

    @Mock
    private PaymentStatusChangedEventPublisher paymentStatusChangedEventPublisher;

    @InjectMocks
    private PaymentStatusOutboxDispatchService paymentStatusOutboxDispatchService;

    @BeforeEach
    void setUp() {
        when(paymentStatusOutboxRepository.claimPending(1L)).thenReturn(true);
    }

    @Test
    void Kafka_발행에_성공하면_Outbox를_전송_완료로_저장한다() {
        PaymentStatusOutbox outbox = pendingOutbox();
        paymentStatusOutboxDispatchService.dispatch(outbox);

        verify(paymentStatusChangedEventPublisher).publish(
            new PaymentStatusChangedEvent(ORDER_ID, PG_ORDER_ID, PaymentStatus.PAID.name()) // <--
        );
        verify(paymentStatusOutboxRepository).save(outbox);
        assertThat(outbox.getStatus()).isEqualTo(PaymentStatusOutboxStatus.SENT);
        assertThat(outbox.getSentAt()).isNotNull();
    }

    @Test
    void Kafka_발행에_실패하면_재시도_횟수를_증가시키고_예외를_전파한다() {
        PaymentStatusOutbox outbox = pendingOutbox();
        doThrow(new IllegalStateException("Kafka 발행 실패"))
            .when(paymentStatusChangedEventPublisher)
            .publish(new PaymentStatusChangedEvent(ORDER_ID, PG_ORDER_ID, PaymentStatus.PAID.name())); // <--

        assertThatThrownBy(() -> paymentStatusOutboxDispatchService.dispatch(outbox))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Kafka 발행 실패");

        verify(paymentStatusOutboxRepository).save(outbox);
        assertThat(outbox.getStatus()).isEqualTo(PaymentStatusOutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
    }

    // 추가 : 즉시 발행 실패가 결제 완료 요청에 영향을 주지 않는지 검증
    @Test
    void 커밋_후_즉시_발행에_실패하면_예외를_전파하지_않고_재시도_횟수를_증가시킨다() {
        PaymentStatusOutbox outbox = pendingOutbox();
        doThrow(new IllegalStateException("Kafka 발행 실패"))
            .when(paymentStatusChangedEventPublisher)
            .publish(new PaymentStatusChangedEvent(ORDER_ID, PG_ORDER_ID, PaymentStatus.PAID.name())); // <--

        paymentStatusOutboxDispatchService.dispatchAfterCommit(outbox);

        verify(paymentStatusOutboxRepository).save(outbox);
        assertThat(outbox.getStatus()).isEqualTo(PaymentStatusOutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
    }

    @Test
    void 이미_다른_처리자가_선점한_Outbox는_Kafka를_발행하지_않는다() {
        PaymentStatusOutbox outbox = pendingOutbox();
        when(paymentStatusOutboxRepository.claimPending(1L)).thenReturn(false);

        paymentStatusOutboxDispatchService.dispatch(outbox);

        verifyNoInteractions(paymentStatusChangedEventPublisher);
        verify(paymentStatusOutboxRepository, never()).save(any());
        assertThat(outbox.getStatus()).isEqualTo(PaymentStatusOutboxStatus.PENDING);
    }

    private PaymentStatusOutbox pendingOutbox() {
        PaymentStatusOutbox outbox = PaymentStatusOutbox.pending(PAYMENT_ID, ORDER_ID, PG_ORDER_ID, PaymentStatus.PAID); // <--
        ReflectionTestUtils.setField(outbox, "id", 1L);
        return outbox;
    }
}
