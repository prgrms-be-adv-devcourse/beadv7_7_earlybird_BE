package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlement;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlementQuery;
import com.growmighty.lectures.firstday.settlement.application.port.toss.TossSettlementReader;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataOrderPaymentFactRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DummyTossSettlementReader implements TossSettlementReader {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SpringDataOrderPaymentFactRepository paymentRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TossSettlement> find(TossSettlementQuery query) {
        if (query.dateType() != TossSettlementQuery.DateType.SOLD_DATE) {
            throw new IllegalArgumentException("현재 토스 정산 더미는 soldDate 조회만 지원합니다.");
        }
        Instant startInclusive = query.startDate().atStartOfDay(SEOUL).toInstant();
        Instant endExclusive = query.endDate().plusDays(1).atStartOfDay(SEOUL).toInstant();
        return paymentRepository.findAllByStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtAscOrderIdAsc(
                        OrderPaymentFact.Status.COMPLETED,
                        startInclusive,
                        endExclusive,
                        PageRequest.of(query.page() - 1, query.size())
                ).stream()
                .map(DummyTossSettlementReader::toSettlement)
                .toList();
    }

    private static TossSettlement toSettlement(OrderPaymentFact payment) {
        ZonedDateTime completedAt = payment.completedAt().atZone(SEOUL);
        return new TossSettlement(
                payment.pgOrderId(),
                "KRW",
                payment.paymentAmount(),
                completedAt.toOffsetDateTime(),
                completedAt.toLocalDate()
        );
    }
}
