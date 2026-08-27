package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlement;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlementQuery;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataOrderPaymentFactRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DummyTossSettlementReaderTest {

    @Test
    @DisplayName("DB 완료 결제를 soldDate 기준으로 결정적 페이지형 토스 정산 내역으로 반환한다")
    void returnsCompletedPaymentsAsSoldDatePage() {
        OrderPaymentFact mismatchPayment = completedPayment(
                2L,
                "PG-UC-91201",
                100_000,
                "2026-08-10T01:00:00Z"
        );
        DummyTossSettlementReader reader = reader(List.of(
                mismatchPayment,
                completedPayment(1L, "PG-UC-91101", 100_000, "2026-08-05T01:00:00Z")
        ));

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
                .containsExactly("KRW", Money.wons(101_000), LocalDate.of(2026, 8, 10));
        assertThat(reader.find(query(2, 1))).isEqualTo(secondPage);
        assertThat(mismatchPayment.paymentAmount()).isEqualTo(Money.wons(100_000));
    }

    @Test
    @DisplayName("불일치 대상 외 완료 결제는 DB 금액 그대로 반환한다")
    void returnsOriginalAmountForNonMismatchPayment() {
        DummyTossSettlementReader reader = reader(List.of(
                completedPayment(1L, "PG-UC-91101", 100_000, "2026-08-05T01:00:00Z")
        ));

        assertThat(reader.find(query(1, 10)).getFirst().amount()).isEqualTo(Money.wons(100_000));
    }

    @Test
    @DisplayName("더미는 paidOutDate 조회를 다른 의미로 해석하지 않는다")
    void rejectsUnsupportedPaidOutDateQuery() {
        DummyTossSettlementReader reader = reader(List.of());

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

    private static DummyTossSettlementReader reader(List<OrderPaymentFact> payments) {
        SpringDataOrderPaymentFactRepository repository = mock(SpringDataOrderPaymentFactRepository.class);
        when(repository.findCompletedInRange(any(), any(), any())).thenReturn(payments);
        return new DummyTossSettlementReader(repository);
    }

    private static OrderPaymentFact completedPayment(Long orderId, String pgOrderId, long amount, String completedAt) {
        return OrderPaymentFact.completed(
                orderId,
                pgOrderId,
                1L,
                Money.wons(amount),
                Instant.parse(completedAt)
        );
    }
}
