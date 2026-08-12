package com.growmighty.lectures.firstday.settlement.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.growmighty.lectures.firstday.common.kafka.KafkaTopics;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectRefundRequested;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectRefundRequestedRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto.ProjectRefundRequestedEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class ProjectRefundRequestedKafkaPublisherTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-01T00:01:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private ProjectRefundRequestedRepository outboxRepository;

    @Mock
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Captor
    private ArgumentCaptor<ProjectRefundRequestedEvent> eventCaptor;

    @Test
    void marksOutboxPublishedOnlyAfterKafkaAcknowledges() {
        ProjectRefundRequested request = request();
        when(outboxRepository.findPending()).thenReturn(List.of(request));
        when(kafkaTemplate.send(eq(KafkaTopics.PAYMENT_BULK_CANCEL_COMMAND), eq(request.refundRequestId()), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher().publishPending();

        verify(kafkaTemplate).send(
                eq(KafkaTopics.PAYMENT_BULK_CANCEL_COMMAND),
                eq(request.refundRequestId()),
                eventCaptor.capture()
        );
        verify(outboxRepository).save(request);
        assertThat(request.published()).isTrue();
        assertThat(eventCaptor.getValue().settlementId()).isEqualTo(request.refundRequestId());
        assertThat(eventCaptor.getValue().payload().settlementId()).isEqualTo(request.refundRequestId());
    }

    @Test
    void retriesSameOutboxWhenKafkaAcknowledgmentFails() {
        ProjectRefundRequested request = request();
        when(outboxRepository.findPending()).thenReturn(List.of(request));
        when(kafkaTemplate.send(eq(KafkaTopics.PAYMENT_BULK_CANCEL_COMMAND), eq(request.refundRequestId()), any()))
                .thenReturn(
                        CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")),
                        CompletableFuture.completedFuture(null)
                );

        publisher().publishPending();
        publisher().publishPending();

        verify(kafkaTemplate, org.mockito.Mockito.times(2)).send(
                eq(KafkaTopics.PAYMENT_BULK_CANCEL_COMMAND),
                eq(request.refundRequestId()),
                eventCaptor.capture()
        );
        verify(outboxRepository).save(request);
        assertThat(request.published()).isTrue();
        assertThat(eventCaptor.getAllValues())
                .extracting(ProjectRefundRequestedEvent::settlementId)
                .containsOnly(request.refundRequestId());
    }

    @Test
    void keepsOutboxPendingWhenKafkaAcknowledgmentTimesOut() {
        ProjectRefundRequested request = request();
        when(outboxRepository.findPending()).thenReturn(List.of(request));
        when(kafkaTemplate.send(eq(KafkaTopics.PAYMENT_BULK_CANCEL_COMMAND), eq(request.refundRequestId()), any()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("broker delayed")));

        publisher().publishPending();

        verify(outboxRepository, never()).save(request);
        assertThat(request.published()).isFalse();
    }

    private ProjectRefundRequestedKafkaPublisher publisher() {
        return new ProjectRefundRequestedKafkaPublisher(outboxRepository, kafkaTemplate, CLOCK);
    }

    private static ProjectRefundRequested request() {
        ProjectOutcomeFact outcome = ProjectOutcomeFact.of(
                101L,
                9L,
                ProjectOutcomeFact.Outcome.FAILED,
                OCCURRED_AT
        );
        OrderPaymentFact payment = OrderPaymentFact.completed(
                1001L,
                "PAY-01J2X8P4QW6YV0M3",
                101L,
                Money.wons(50_000),
                OCCURRED_AT.minusSeconds(60)
        );
        return ProjectRefundRequested.request(
                "3bdb6b7d-ec38-4e66-b8a6-b0fc37cb67d1",
                outcome,
                List.of(payment),
                OCCURRED_AT
        );
    }
}
