package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlement;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlementQuery;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlementReader;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class DummyTossSettlementReader implements TossSettlementReader {

    private final List<TossSettlement> settlements;

    public DummyTossSettlementReader(
            ObjectMapper objectMapper,
            @Value("${settlement.dummy-pg.fixture-location:classpath:/fixtures/pg/empty.json}") Resource fixture
    ) {
        try {
            settlements = Arrays.stream(objectMapper.readValue(fixture.getInputStream(), Fixture[].class))
                    .map(Fixture::toSettlement)
                    .sorted(Comparator.comparing(TossSettlement::soldDate).thenComparing(TossSettlement::orderId))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("PG fixture를 읽을 수 없습니다: " + fixture.getDescription(), exception);
        }
    }

    @Override
    public List<TossSettlement> find(TossSettlementQuery query) {
        if (query.dateType() != TossSettlementQuery.DateType.SOLD_DATE) {
            throw new IllegalArgumentException("현재 토스 정산 더미는 soldDate 조회만 지원합니다.");
        }
        return settlements.stream()
                .filter(settlement -> !settlement.soldDate().isBefore(query.startDate()))
                .filter(settlement -> !settlement.soldDate().isAfter(query.endDate()))
                .skip((long) (query.page() - 1) * query.size())
                .limit(query.size())
                .toList();
    }

    public record Fixture(String orderId, long amount, OffsetDateTime approvedAt, LocalDate soldDate) {

        TossSettlement toSettlement() {
            return new TossSettlement(orderId, "KRW", Money.wons(amount), approvedAt, soldDate);
        }
    }
}
