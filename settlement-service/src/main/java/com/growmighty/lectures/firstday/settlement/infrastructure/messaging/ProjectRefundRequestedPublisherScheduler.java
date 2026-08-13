package com.growmighty.lectures.firstday.settlement.infrastructure.messaging;

import com.growmighty.lectures.firstday.settlement.infrastructure.kafka.ProjectRefundRequestedKafkaPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectRefundRequestedPublisherScheduler {

    private final ProjectRefundRequestedKafkaPublisher publisher;

    @Scheduled(fixedDelayString = "${settlement.refund-outbox.publish-fixed-delay:60000}")
    public void publishPending() {
        publisher.publishPending();
    }
}
