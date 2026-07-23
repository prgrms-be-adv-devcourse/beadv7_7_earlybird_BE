package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import com.growmighty.lectures.firstday.settlement.application.port.FinalEffectivePaymentAmountReader;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "settlement.external-data.mode",
        havingValue = "dummy",
        matchIfMissing = true
)
public class DummyFinalEffectivePaymentAmountReader implements FinalEffectivePaymentAmountReader {

    @Override
    public List<Money> findFinalEffectivePaymentAmounts(Long projectId) {
        if (projectId != DummyProjectSettlementTargetReader.DUMMY_PROJECT_ID) {
            throw new IllegalArgumentException("더미 결제 정보가 없는 프로젝트입니다: " + projectId);
        }
        return List.of(Money.wons(100_000));
    }
}
