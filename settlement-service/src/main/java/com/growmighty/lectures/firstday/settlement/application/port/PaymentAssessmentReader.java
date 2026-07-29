package com.growmighty.lectures.firstday.settlement.application.port;

import java.util.List;
import java.util.Set;

public interface PaymentAssessmentReader {

    List<PaymentAssessment> findPaymentAssessments(Set<Long> orderIds);
}
