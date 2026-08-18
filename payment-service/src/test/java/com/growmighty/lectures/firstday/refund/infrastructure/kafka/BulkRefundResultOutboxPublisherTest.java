package com.growmighty.lectures.firstday.refund.infrastructure.kafka;

import com.growmighty.lectures.firstday.common.kafka.KafkaTopics;
import com.growmighty.lectures.firstday.payment.infrastructure.kafka.dto.ProjectRefundProcessedEvent;
import com.growmighty.lectures.firstday.refund.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkRefundResultOutboxPublisherTest {

    private static final Long FIRST_SETTLEMENT_ID = 10L;

    @Mock
    private BulkRefundResultOutboxRepository bulkRefundResultOutboxRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private KafkaTemplate<String, ProjectRefundProcessedEvent> kafkaTemplate;

    @InjectMocks
    private BulkRefundResultOutboxPublisher publisher;

    // 변경 : 같은 일괄 취소의 성공·실패 주문을 상태별 Kafka 이벤트로 분리
    @Test
    void publishPending_groupsOrderIdsByRefundStatus() {
        BulkRefundResultOutbox first = BulkRefundResultOutbox.pending(
            FIRST_SETTLEMENT_ID,
            BulkRefundResultStatus.COMPLETED
        );
        BulkRefundResultOutbox second = BulkRefundResultOutbox.pending(
            FIRST_SETTLEMENT_ID,
            BulkRefundResultStatus.FAILED
        );
        when(bulkRefundResultOutboxRepository.findPending(100)).thenReturn(List.of(first, second));
        when(refundRepository.findOrdersBySettlementIds(List.of(FIRST_SETTLEMENT_ID, FIRST_SETTLEMENT_ID)))
            .thenReturn(List.of(
                new BulkRefundOrder(FIRST_SETTLEMENT_ID, 101L, RefundStatus.COMPLETED),
                new BulkRefundOrder(FIRST_SETTLEMENT_ID, 102L, RefundStatus.FAILED)
            ));
        when(kafkaTemplate.send(
            eq(KafkaTopics.PAYMENT_BULK_CANCEL_RESULT),
            any(String.class),
            any(ProjectRefundProcessedEvent.class)
        )).thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPending();

        ArgumentCaptor<ProjectRefundProcessedEvent> captor =
            ArgumentCaptor.forClass(ProjectRefundProcessedEvent.class);
        verify(refundRepository).findOrdersBySettlementIds(List.of(FIRST_SETTLEMENT_ID, FIRST_SETTLEMENT_ID));
        verify(kafkaTemplate, times(2)).send(
            eq(KafkaTopics.PAYMENT_BULK_CANCEL_RESULT),
            any(String.class),
            captor.capture()
        );
        verify(bulkRefundResultOutboxRepository, times(2)).save(any(BulkRefundResultOutbox.class));
        assertThat(first.getOutboxStatus()).isEqualTo(BulkRefundResultOutboxStatus.SENT);
        assertThat(second.getOutboxStatus()).isEqualTo(BulkRefundResultOutboxStatus.SENT);
        assertThat(captor.getAllValues())
            .extracting(event -> event.payload().orderIds())
            .containsExactly(List.of(101L), List.of(102L));
        assertThat(captor.getAllValues())
            .extracting(event -> event.payload().status())
            .containsExactly("COMPLETED", "FAILED");
        assertThat(captor.getAllValues())
            .extracting(ProjectRefundProcessedEvent::eventId)
            .doesNotHaveDuplicates();
    }

    // 추가 : Kafka 발행 실패 시 결과 Outbox를 PENDING으로 유지하고 재시도 횟수 증가
    @Test
    void publishPending_keepsOutboxPendingWhenKafkaPublishFails() {
        BulkRefundResultOutbox outbox = BulkRefundResultOutbox.pending(
            FIRST_SETTLEMENT_ID,
            BulkRefundResultStatus.COMPLETED
        );
        CompletableFuture<SendResult<String, ProjectRefundProcessedEvent>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new IllegalStateException("Kafka unavailable"));
        when(bulkRefundResultOutboxRepository.findPending(100)).thenReturn(List.of(outbox));
        when(refundRepository.findOrdersBySettlementIds(List.of(FIRST_SETTLEMENT_ID)))
            .thenReturn(List.of(new BulkRefundOrder(FIRST_SETTLEMENT_ID, 101L, RefundStatus.COMPLETED)));
        when(kafkaTemplate.send(
            eq(KafkaTopics.PAYMENT_BULK_CANCEL_RESULT),
            eq(String.valueOf(FIRST_SETTLEMENT_ID)),
            any(ProjectRefundProcessedEvent.class)
        )).thenReturn(failedFuture);

        publisher.publishPending();

        assertThat(outbox.getOutboxStatus()).isEqualTo(BulkRefundResultOutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        verify(bulkRefundResultOutboxRepository).save(outbox);
    }
}
