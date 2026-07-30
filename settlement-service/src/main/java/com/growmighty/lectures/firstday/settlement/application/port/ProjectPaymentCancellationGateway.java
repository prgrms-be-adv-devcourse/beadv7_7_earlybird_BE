package com.growmighty.lectures.firstday.settlement.application.port;

import java.util.List;

public interface ProjectPaymentCancellationGateway {

    List<ProjectPaymentCancellationResult> cancel(List<ProjectPaymentCancellationRequest> requests);
}
