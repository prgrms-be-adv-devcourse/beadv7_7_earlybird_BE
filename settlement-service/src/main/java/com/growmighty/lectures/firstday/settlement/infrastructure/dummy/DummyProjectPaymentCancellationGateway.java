package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationGateway;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationRequest;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationResult;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationStatus;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "settlement.external-data.mode",
        havingValue = "dummy",
        matchIfMissing = true
)
public class DummyProjectPaymentCancellationGateway implements ProjectPaymentCancellationGateway {

    @Override
    public List<ProjectPaymentCancellationResult> cancel(
            List<ProjectPaymentCancellationRequest> requests
    ) {
        return requests.stream()
                .map(ProjectPaymentCancellationRequest::orderId)
                .map(orderId -> new ProjectPaymentCancellationResult(
                        orderId,
                        ProjectPaymentCancellationStatus.COMPLETED
                ))
                .toList();
    }
}
