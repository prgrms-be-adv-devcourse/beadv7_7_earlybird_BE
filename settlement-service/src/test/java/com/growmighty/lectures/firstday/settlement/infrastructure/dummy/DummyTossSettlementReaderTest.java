package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlement;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlementQuery;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataOrderPaymentFactRepository;
import com.growmighty.lectures.firstday.settlement.support.MySqlIntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create")
class DummyTossSettlementReaderTest extends MySqlIntegrationTestSupport {

    @Autowired
    private SpringDataOrderPaymentFactRepository paymentRepository;

    @Test
    @DisplayName("저장된 완료 주문 결제 사실을 soldDate 기준으로 페이지형 토스 정산 내역으로 반환한다")
    void returnsCompletedPaymentsAsSoldDatePage() {
        paymentRepository.saveAllAndFlush(List.of(
                completed(1001L, "PAY-JULY-2", "2026-07-15T13:20:10Z", 50_000),
                completed(1002L, "PAY-JULY-1", "2026-07-15T13:20:09Z", 40_000),
                completed(1003L, "PAY-AUGUST", "2026-08-01T00:00:00Z", 30_000)
        ));
        OrderPaymentFact cancelled = completed(
                1004L,
                "PAY-CANCELLED",
                "2026-07-16T00:00:00Z",
                20_000
        );
        cancelled.cancel(
                "PAY-CANCELLED",
                104L,
                Money.wons(20_000),
                Instant.parse("2026-07-17T00:00:00Z")
        );
        paymentRepository.saveAndFlush(cancelled);
        DummyTossSettlementReader reader = new DummyTossSettlementReader(paymentRepository);

        List<TossSettlement> firstPage = reader.find(query(1, 1));
        List<TossSettlement> secondPage = reader.find(query(2, 1));

        assertThat(firstPage).extracting(TossSettlement::orderId).containsExactly("PAY-JULY-1");
        assertThat(secondPage).extracting(TossSettlement::orderId).containsExactly("PAY-JULY-2");
        assertThat(firstPage.getFirst())
                .extracting(
                        TossSettlement::currency,
                        TossSettlement::amount,
                        TossSettlement::soldDate
                )
                .containsExactly("KRW", Money.wons(40_000), LocalDate.of(2026, 7, 15));
    }

    @Test
    @DisplayName("더미는 paidOutDate 조회를 다른 의미로 해석하지 않는다")
    void rejectsUnsupportedPaidOutDateQuery() {
        DummyTossSettlementReader reader = new DummyTossSettlementReader(paymentRepository);

        assertThatThrownBy(() -> reader.find(new TossSettlementQuery(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                TossSettlementQuery.DateType.PAID_OUT_DATE,
                1,
                1
        ))).isInstanceOf(IllegalArgumentException.class);
    }

    private static TossSettlementQuery query(int page, int size) {
        return new TossSettlementQuery(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                TossSettlementQuery.DateType.SOLD_DATE,
                page,
                size
        );
    }

    private static OrderPaymentFact completed(
            Long orderId,
            String pgOrderId,
            String completedAt,
            long paymentAmount
    ) {
        return OrderPaymentFact.completed(
                orderId,
                pgOrderId,
                orderId - 900L,
                Money.wons(paymentAmount),
                Instant.parse(completedAt)
        );
    }
}
