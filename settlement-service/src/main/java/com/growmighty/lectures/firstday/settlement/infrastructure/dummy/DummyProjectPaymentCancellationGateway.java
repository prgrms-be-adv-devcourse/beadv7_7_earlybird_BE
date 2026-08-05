package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import com.growmighty.lectures.firstday.settlement.application.port.payment.ProjectPaymentCancellationGateway;
import com.growmighty.lectures.firstday.settlement.application.port.payment.ProjectPaymentCancellationRequest;
import com.growmighty.lectures.firstday.settlement.application.port.payment.ProjectPaymentCancellationResult;
import com.growmighty.lectures.firstday.settlement.application.port.payment.ProjectPaymentCancellationStatus;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "settlement.payment-cancellation.mode",
        havingValue = "dummy"
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
