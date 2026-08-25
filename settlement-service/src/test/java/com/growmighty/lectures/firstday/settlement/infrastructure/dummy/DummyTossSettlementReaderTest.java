package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlement;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlementQuery;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class DummyTossSettlementReaderTest {

    @Test
    @DisplayName("독립 fixture를 soldDate 기준으로 결정적 페이지형 토스 정산 내역으로 반환한다")
    void returnsIndependentFixtureAsSoldDatePage() {
        DummyTossSettlementReader reader = reader("fixtures/pg/f02-mismatch.json");

        List<TossSettlement> firstPage = reader.find(query(1, 1));
        List<TossSettlement> secondPage = reader.find(query(2, 1));

        assertThat(firstPage).extracting(TossSettlement::orderId).containsExactly("PG-UC-91101");
        assertThat(secondPage).extracting(TossSettlement::orderId).containsExactly("PG-UC-91201");
        assertThat(secondPage.getFirst())
                .extracting(
                        TossSettlement::currency,
                        TossSettlement::amount,
                        TossSettlement::soldDate
                )
                .containsExactly("KRW", Money.wons(90_000), LocalDate.of(2026, 8, 10));
        assertThat(reader.find(query(2, 1))).isEqualTo(secondPage);
    }

    @Test
    @DisplayName("더미는 paidOutDate 조회를 다른 의미로 해석하지 않는다")
    void rejectsUnsupportedPaidOutDateQuery() {
        DummyTossSettlementReader reader = reader("fixtures/pg/empty.json");

        assertThatThrownBy(() -> reader.find(new TossSettlementQuery(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                TossSettlementQuery.DateType.PAID_OUT_DATE,
                1,
                1
        ))).isInstanceOf(IllegalArgumentException.class);
    }

    private static TossSettlementQuery query(int page, int size) {
        return new TossSettlementQuery(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                TossSettlementQuery.DateType.SOLD_DATE,
                page,
                size
        );
    }

    private static DummyTossSettlementReader reader(String fixture) {
        return new DummyTossSettlementReader(
                new ObjectMapper().findAndRegisterModules(),
                new ClassPathResource(fixture)
        );
    }
}
