package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import com.growmighty.lectures.firstday.settlement.application.port.PaymentAssessment;
import com.growmighty.lectures.firstday.settlement.application.port.PaymentAssessmentReader;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "settlement.external-data.mode",
        havingValue = "dummy",
        matchIfMissing = true
)
public class DummyPaymentAssessmentReader implements PaymentAssessmentReader {

    @Override
    public List<PaymentAssessment> findPaymentAssessments(Set<Long> orderIds) {
        if (!orderIds.equals(Set.of(DummyProjectOrderReader.DUMMY_ORDER_ID))) {
            throw new IllegalArgumentException("더미 결제 정보가 없는 주문입니다: " + orderIds);
        }
        return List.of(PaymentAssessment.ready(
                DummyProjectOrderReader.DUMMY_ORDER_ID,
                Money.wons(100_000)
        ));
    }
}
