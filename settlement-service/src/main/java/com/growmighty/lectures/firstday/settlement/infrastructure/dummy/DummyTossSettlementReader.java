package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlement;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlementQuery;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlementReader;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataOrderPaymentFactRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DummyTossSettlementReader implements TossSettlementReader {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // PG 불일치 시나리오 대상: 테스트 대상 결제를 바꿀 때 이 pgOrderId를 변경한다.
    private static final String MISMATCH_PG_ORDER_ID = "PG-UC-91201";
    private static final BigDecimal MISMATCH_AMOUNT = BigDecimal.valueOf(1_000);

    private final SpringDataOrderPaymentFactRepository paymentRepository;

    @Override
    public List<TossSettlement> find(TossSettlementQuery query) {
        if (query.dateType() != TossSettlementQuery.DateType.SOLD_DATE) {
            throw new IllegalArgumentException("현재 토스 정산 더미는 soldDate 조회만 지원합니다.");
        }
        Instant startInclusive = query.startDate().atStartOfDay(SEOUL).toInstant();
        Instant endExclusive = query.endDate().plusDays(1).atStartOfDay(SEOUL).toInstant();
        return paymentRepository.findCompletedInRange(startInclusive, endExclusive, Pageable.unpaged()).stream()
                .map(this::toSettlement)
                .sorted(Comparator.comparing(TossSettlement::soldDate).thenComparing(TossSettlement::orderId))
                .skip((long) (query.page() - 1) * query.size())
                .limit(query.size())
                .toList();
    }

    private TossSettlement toSettlement(OrderPaymentFact payment) {
        Money amount = payment.pgOrderId().equals(MISMATCH_PG_ORDER_ID)
                ? Money.wons(payment.paymentAmount().amount().add(MISMATCH_AMOUNT))
                : payment.paymentAmount();
        return new TossSettlement(
                payment.pgOrderId(),
                "KRW",
                amount,
                payment.completedAt().atZone(SEOUL).toOffsetDateTime(),
                payment.completedAt().atZone(SEOUL).toLocalDate()
        );
    }
}
