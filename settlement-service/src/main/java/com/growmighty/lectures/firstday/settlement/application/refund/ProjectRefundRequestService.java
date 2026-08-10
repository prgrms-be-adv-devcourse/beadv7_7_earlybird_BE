package com.growmighty.lectures.firstday.settlement.application.refund;

import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectRefundRequested;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectRefundInputRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectRefundRequestedRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectRefundRequestService {

    private final ProjectRefundInputRepository inputRepository;
    private final ProjectRefundRequestedRepository outboxRepository;
    private final Clock clock;

    @Transactional
    public List<ProjectRefundRequested> createDueRequests() {
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock);
        List<ProjectRefundRequested> created = new ArrayList<>();
        for (ProjectOutcomeFact outcome : inputRepository.findRefundOutcomes()) {
            LocalDate dueDate = outcome.occurredAt().atZone(clock.getZone()).toLocalDate().plusDays(1);
            if (dueDate.isAfter(today) || outboxRepository.findByProjectId(outcome.projectId()).isPresent()) {
                continue;
            }
            List<OrderPaymentFact> payments = inputRepository.findPayments(outcome.projectId());
            if (!hasCompleteInput(outcome, payments)) {
                continue;
            }
            List<OrderPaymentFact> refundablePayments = payments.stream()
                    .filter(payment -> payment.status() == OrderPaymentFact.Status.COMPLETED)
                    .toList();
            if (refundablePayments.isEmpty()) {
                continue;
            }
            created.add(outboxRepository.save(ProjectRefundRequested.request(
                    UUID.randomUUID().toString(),
                    outcome,
                    refundablePayments,
                    now
            )));
        }
        return List.copyOf(created);
    }

    private static boolean hasCompleteInput(
            ProjectOutcomeFact outcome,
            List<OrderPaymentFact> payments
    ) {
        return !payments.isEmpty() && payments.stream().allMatch(payment ->
                Objects.equals(outcome.projectId(), payment.projectId())
                        && !payment.completedAt().isAfter(outcome.occurredAt())
                        && (payment.cancelledAt() == null
                        || !payment.cancelledAt().isAfter(outcome.occurredAt()))
        );
    }
}
