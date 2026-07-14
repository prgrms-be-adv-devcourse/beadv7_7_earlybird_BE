package com.growmighty.lectures.firstday.settlement.batch;

import com.growmighty.lectures.firstday.settlement.read.Order;
import com.growmighty.lectures.firstday.settlement.read.OrderStatus;
import com.growmighty.lectures.firstday.settlement.domain.Settlement;
import com.growmighty.lectures.firstday.settlement.domain.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

/**
 * [ItemProcessor] 읽어온 주문 1건을 정산 1건으로 "가공"한다.
 *
 * <p>Chunk 지향 처리의 가운데 단계. Reader 가 준 {@link Order} 를 받아
 * {@link Settlement} 로 변환한다. 1원 무결성 계산은 {@link Settlement#of} 에 그대로 위임한다.
 *
 * <p><b>[Step3] 멱등성(Idempotency)</b>: 이미 정산된 주문은 {@link SettlementRepository#existsByOrderId}
 * 로 걸러 {@code null} 을 반환한다(= Writer 로 안 넘김 → 필터링). 덕분에 배치를 몇 번을 다시 돌려도
 * 한 주문은 한 번만 정산된다. "다시 실행하면 이미 정산된 50%는 건너뛰고 나머지 50%만" 의 핵심.
 * (DB 의 {@code uk_settlement_order_id} 유니크 제약은 최후의 안전망, 이 스킵이 1차 방어선이다.)
 *
 * <p><b>[Step3] 장애 주입</b>: {@link SettlementFaultBox} 가 장전돼 있으면, 정해진 건수를
 * 넘기는 순간 {@link SettlementFaultException} 을 던져 Step 을 50% 지점에서 실패시킨다.
 *
 * <p>이 빈은 {@code @StepScope} 다. Step 실행마다 새로 만들어져 {@link #produced} 카운터가
 * 매번 0 으로 초기화된다 → 재시작 때도 깨끗한 상태에서 다시 센다.
 *
 * <p>변환할 수 없는 주문(결제 안 됨/결제 id 없음)도 {@code null} 을 반환해 필터링한다.
 */
@Component
@StepScope
@RequiredArgsConstructor
public class OrderToSettlementProcessor implements ItemProcessor<Order, Settlement> {

    /** 플랫폼 수수료율 3% */
    private static final BigDecimal FEE_RATE = new BigDecimal("0.03");

    private final SettlementRepository settlementRepository;
    private final SettlementFaultBox faultBox;

    /** 이번 Step 실행에서 실제로 만들어낸 정산 건수(스킵 제외). @StepScope 라 실행마다 0 부터. */
    private final AtomicLong produced = new AtomicLong();

    @Override
    public Settlement process(Order order) {
        // [멱등성] 이미 정산된 주문이면 건너뜀 → 재실행/재시작 시 중복 정산 방지
        if (settlementRepository.existsByOrderId(order.getId())) {
            return null;
        }
        if (order.getStatus() != OrderStatus.PAID || order.getPaymentId() == null) {
            return null; // 정산 대상 아님 → 필터링
        }

        // [장애 주입] 정해진 건수를 넘기는 순간 강제 실패 → Step FAILED (직전 chunk 까지는 커밋됨)
        long n = produced.incrementAndGet();
        if (faultBox.armed() && n > faultBox.failAfter()) {
            throw new SettlementFaultException(
                    "의도적 장애: %d건 정산 직후 강제 실패 (이 chunk 는 롤백)".formatted(faultBox.failAfter()));
        }

        BigDecimal amount = order.getTotalAmount().getValue();
        return Settlement.of(order.getId(), order.getPaymentId(), amount, FEE_RATE);
    }
}
