package com.growmighty.lectures.firstday.settlement.infrastructure.messaging;

import com.growmighty.lectures.firstday.settlement.application.refund.ProjectRefundRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectRefundRequestScheduler {

    private final ProjectRefundRequestService service;

    @Scheduled(cron = "${settlement.refund-outbox.create-cron:0 5 0 * * *}", zone = "Asia/Seoul")
    public void createDueRequests() {
        service.createDueRequests();
    }
}
