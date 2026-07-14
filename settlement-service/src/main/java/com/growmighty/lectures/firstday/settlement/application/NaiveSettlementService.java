package com.growmighty.lectures.firstday.settlement.application;

import com.growmighty.lectures.firstday.settlement.read.Order;
import com.growmighty.lectures.firstday.settlement.read.OrderRepository;
import com.growmighty.lectures.firstday.settlement.read.OrderStatus;
import com.growmighty.lectures.firstday.settlement.application.dto.SettleReport;
import com.growmighty.lectures.firstday.settlement.domain.Settlement;
import com.growmighty.lectures.firstday.settlement.domain.SettlementRepository;
import com.growmighty.lectures.firstday.settlement.support.HeapMonitor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * [의도적인 안티패턴] "배치가 왜 필요한가" 를 몸으로 느끼게 해주는 정산 서비스.
 *
 * <p>두 가지 데모를 제공한다.
 * <ol>
 *   <li>{@link #settleAll()} — "한 줄의 함정". {@code findAll()} 로 전량을 한 번에 메모리로 올린다.
 *       대용량에서 그 한 줄에서 바로 <b>OutOfMemoryError</b>.</li>
 *   <li>{@link #settleUpTo(int)} — "절벽으로 걸어가기". 페이지로 조금씩 읽되 <b>전부 메모리에 쌓으며</b>
 *       정산한다. 페이지마다 메모리/시간을 로그로 찍어, limit 을 올려갈수록 메모리와 시간이
 *       어떻게 증가하는지 추세를 보여준다. limit 을 충분히 키우면 OOM 으로 이어진다.</li>
 * </ol>
 *
 * <p>둘 다 {@link HeapMonitor} 로 감싸 콘솔에서 힙이 차오르는 과정을 실시간으로 볼 수 있다.
 * 운영에서는 절대 쓰지 말 것.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaiveSettlementService {

    /** 플랫폼 수수료율 3% */
    private static final BigDecimal FEE_RATE = new BigDecimal("0.03");

    /** 한 번에 읽어오는 페이지 크기 */
    private static final int PAGE_SIZE = 10_000;

    private final OrderRepository orderRepository;
    private final SettlementRepository settlementRepository;

    /**
     * [데모1] "한 줄의 함정" — 전량을 메모리로 적재(findAll)한 뒤 정산.
     * 100만 건이면 findAll 그 한 줄에서 OOM 이 발생한다.
     */
    @Transactional
    public SettleReport settleAll() {
        try (HeapMonitor monitor = HeapMonitor.start("naive-findAll", 500)) {
            long startedAt = System.currentTimeMillis();
            log.warn("[NAIVE] findAll() 시작 — 전체 주문을 한 번에 메모리로 올립니다. (대용량이면 여기서 OOM)");

            // ⚠️ 바로 이 한 줄이 100만 엔티티를 통째로 힙에 올린다.
            List<Order> orders = orderRepository.findAll();
            log.warn("[NAIVE] findAll() 완료 — 적재된 주문 수 = {}", orders.size());

            long settled = settleEach(orders);
            long elapsed = System.currentTimeMillis() - startedAt;

            SettleReport report = new SettleReport(
                    orders.size(), settled, 0, elapsed, monitor.peakUsedMb(), monitor.maxHeapMb(), "COMPLETED");
            log.warn("[NAIVE] 완료 리포트 = {}", report);
            return report;
        }
    }

    /**
     * [데모2] "절벽으로 걸어가기" — 페이지로 조금씩 읽되 전부 메모리에 누적하며 정산.
     * limit 을 올려갈수록 메모리/시간이 어떻게 늘어나는지 추세를 보여준다.
     *
     * @param limit 정산할 최대 주문 수 (전체를 보려면 매우 크게)
     */
    @Transactional
    public SettleReport settleUpTo(int limit) {
        try (HeapMonitor monitor = HeapMonitor.start("naive-climb", 500)) {
            long startedAt = System.currentTimeMillis();
            log.warn("[NAIVE] '절벽으로 걸어가기' 시작 — limit={} 까지 메모리에 쌓으며 정산", limit);

            // 안티패턴 재현: 읽은 주문을 절대 버리지 않고 계속 보관한다.
            List<Order> holding = new ArrayList<>();
            long settled = 0;
            int page = 0;

            while (holding.size() < limit) {
                List<Order> batch = orderRepository.findPage(page++, PAGE_SIZE);
                if (batch.isEmpty()) {
                    break;
                }
                holding.addAll(batch);
                settled += settleEach(batch);

                long elapsed = System.currentTimeMillis() - startedAt;
                log.warn("[NAIVE] 누적 {}건 보관 / 정산 {}건 / 경과 {}ms (메모리는 위 [mem] 로그 참고)",
                        holding.size(), settled, elapsed);
            }

            long elapsed = System.currentTimeMillis() - startedAt;
            SettleReport report = new SettleReport(
                    holding.size(), settled, 0, elapsed, monitor.peakUsedMb(), monitor.maxHeapMb(), "COMPLETED");
            log.warn("[NAIVE] 완료 리포트 = {}", report);
            return report;
        }
    }

    private long settleEach(List<Order> orders) {
        long settled = 0;
        for (Order order : orders) {
            if (order.getStatus() != OrderStatus.PAID || order.getPaymentId() == null) {
                continue;
            }
            BigDecimal amount = order.getTotalAmount().getValue();
            settlementRepository.save(Settlement.of(order.getId(), order.getPaymentId(), amount, FEE_RATE));
            settled++;
        }
        return settled;
    }
}
