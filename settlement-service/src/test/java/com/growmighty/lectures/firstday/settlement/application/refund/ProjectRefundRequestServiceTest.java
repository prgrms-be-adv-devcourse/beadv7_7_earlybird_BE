package com.growmighty.lectures.firstday.settlement.application.refund;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectCancellationReason;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectRefundRequested;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectRefundInputRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectRefundRequestedRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataOrderPaymentFactRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataProjectOutcomeFactRepository;
import com.growmighty.lectures.firstday.settlement.support.MySqlIntegrationTestSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ProjectRefundRequestServiceTest extends MySqlIntegrationTestSupport {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-10T00:05:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Autowired
    private ProjectRefundInputRepository inputRepository;

    @Autowired
    private ProjectRefundRequestedRepository outboxRepository;

    @Autowired
    private SpringDataProjectOutcomeFactRepository outcomeRepository;

    @Autowired
    private SpringDataOrderPaymentFactRepository paymentRepository;

    @Test
    @DisplayName("결과 이벤트 뒤에 도착한 사전 결제를 포함해 프로젝트별 batch Outbox를 한 번 저장한다")
    void createsOneBatchFromAllPaymentsOccurredBeforeOutcome() {
        long projectId = 6_101L;
        Instant outcomeAt = Instant.parse("2026-08-08T09:00:00Z");
        outcomeRepository.saveAndFlush(ProjectOutcomeFact.of(
                projectId,
                701L,
                ProjectOutcomeFact.Outcome.FAILED,
                outcomeAt
        ));
        paymentRepository.saveAndFlush(OrderPaymentFact.completed(
                61_001L,
                "PG-61001",
                projectId,
                Money.wons(50_000),
                outcomeAt.minusSeconds(60)
        ));
        paymentRepository.saveAndFlush(OrderPaymentFact.completed(
                61_002L,
                "PG-61002",
                projectId,
                Money.wons(30_000),
                outcomeAt.minusSeconds(30)
        ));
        OrderPaymentFact cancelled = OrderPaymentFact.completed(
                61_003L,
                "PG-61003",
                projectId,
                Money.wons(20_000),
                outcomeAt.minusSeconds(90)
        );
        cancelled.cancel("PG-61003", projectId, Money.wons(20_000), outcomeAt.minusSeconds(10));
        paymentRepository.saveAndFlush(cancelled);
        ProjectRefundRequestService service = service();

        List<ProjectRefundRequested> first = service.createDueRequests();
        List<ProjectRefundRequested> repeated = service.createDueRequests();

        assertThat(first).singleElement().satisfies(request -> {
            assertThat(request.projectId()).isEqualTo(projectId);
            assertThat(request.reason()).isEqualTo(ProjectCancellationReason.PROJECT_FAILED);
            assertThat(request.payments())
                    .extracting(ProjectRefundRequested.Payment::orderId)
                    .containsExactly(61_001L, 61_002L);
        });
        assertThat(repeated).isEmpty();
        assertThat(outboxRepository.findByProjectId(projectId).orElseThrow().eventId())
                .isEqualTo(first.getFirst().eventId());
    }

    @Test
    @DisplayName("결제 사실이 아직 없으면 부분 환불 Outbox를 만들지 않는다")
    void waitsForCompletePaymentList() {
        long projectId = 6_102L;
        outcomeRepository.saveAndFlush(ProjectOutcomeFact.of(
                projectId,
                702L,
                ProjectOutcomeFact.Outcome.CANCELLED,
                Instant.parse("2026-08-08T09:00:00Z")
        ));

        service().createDueRequests();

        assertThat(outboxRepository.findByProjectId(projectId)).isEmpty();
    }

    @Test
    @DisplayName("프로젝트 결과 뒤에 발생한 결제가 있으면 계약 충돌로 남기고 Outbox를 만들지 않는다")
    void rejectsPaymentOccurredAfterOutcome() {
        long projectId = 6_103L;
        Instant outcomeAt = Instant.parse("2026-08-08T09:00:00Z");
        outcomeRepository.saveAndFlush(ProjectOutcomeFact.of(
                projectId,
                703L,
                ProjectOutcomeFact.Outcome.FAILED,
                outcomeAt
        ));
        paymentRepository.saveAndFlush(OrderPaymentFact.completed(
                61_004L,
                "PG-61004",
                projectId,
                Money.wons(50_000),
                outcomeAt.plusSeconds(1)
        ));

        service().createDueRequests();

        assertThat(outboxRepository.findByProjectId(projectId)).isEmpty();
    }

    private ProjectRefundRequestService service() {
        return new ProjectRefundRequestService(inputRepository, outboxRepository, CLOCK);
    }
}
