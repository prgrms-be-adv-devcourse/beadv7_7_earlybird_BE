package com.growmighty.lectures.firstday.settlement.application.refund;

import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectRefundRequested;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectRefundInputRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectRefundRequestedRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectRefundRequestService {

    private static final int OUTCOME_BATCH_SIZE = 100;

    private final ProjectRefundInputRepository inputRepository;
    private final ProjectRefundRequestedRepository outboxRepository;
    private final Clock clock;

    @Transactional
    public List<ProjectRefundRequested> createDueRequests() {
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock);
        ZoneId zone = clock.getZone();
        Instant dueBefore = today.atStartOfDay(zone).toInstant();
        List<ProjectRefundRequested> created = new ArrayList<>();
        // ponytail: oldest incomplete inputs can fill one run; add a persisted retry cursor if that backlog grows.
        for (ProjectOutcomeFact outcome : inputRepository.findRefundOutcomes(dueBefore, OUTCOME_BATCH_SIZE)) {
            List<OrderPaymentFact> payments = inputRepository.findPayments(outcome.projectId());
            List<OrderPaymentFact> refundablePayments = outcome.refundablePaymentsDueBefore(dueBefore, payments);
            if (refundablePayments.isEmpty()) {
                continue;
            }
            created.add(outboxRepository.save(ProjectRefundRequested.request(
                    null,
                    outcome,
                    refundablePayments,
                    now
            )));
        }
        return List.copyOf(created);
    }
}
