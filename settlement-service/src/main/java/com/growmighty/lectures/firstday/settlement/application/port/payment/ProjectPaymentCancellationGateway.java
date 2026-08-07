// TODO(settlement-plan): Delete this direct Payment seam after ProjectRefundRequested batch publishing replaces it.
package com.growmighty.lectures.firstday.settlement.application.port.payment;

import java.util.List;

public interface ProjectPaymentCancellationGateway {

    List<ProjectPaymentCancellationResult> cancel(List<ProjectPaymentCancellationRequest> requests);
}
