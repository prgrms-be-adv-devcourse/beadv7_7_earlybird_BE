// TODO(settlement-plan): Add pgOrderId and status fixtures and reuse the same validation shape as Feign recovery.
package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPayment;
import com.growmighty.lectures.firstday.settlement.application.port.order.ProjectOrderReader;
import com.growmighty.lectures.firstday.settlement.application.port.order.ProjectOrders;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "settlement.project-order.mode",
        havingValue = "dummy"
)
public class DummyProjectOrderReader implements ProjectOrderReader {

    static final long DUMMY_ORDER_ID = 9_100_001L;

    @Override
    public List<ProjectOrders> findProjectOrders(Set<Long> projectIds) {
        if (!projectIds.equals(Set.of(DummyProjectSettlementTargetReader.DUMMY_PROJECT_ID))) {
            throw new IllegalArgumentException("더미 주문 정보가 없는 프로젝트입니다: " + projectIds);
        }
        return List.of(new ProjectOrders(
                DummyProjectSettlementTargetReader.DUMMY_PROJECT_ID,
                List.of(new OrderPayment(DUMMY_ORDER_ID, Money.wons(100_000)))
        ));
    }
}
