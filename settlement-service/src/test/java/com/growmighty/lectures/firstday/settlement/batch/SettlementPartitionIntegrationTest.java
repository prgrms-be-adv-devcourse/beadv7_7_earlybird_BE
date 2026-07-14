package com.growmighty.lectures.firstday.settlement.batch;

import com.growmighty.lectures.firstday.settlement.application.SettlementBatchService;
import com.growmighty.lectures.firstday.settlement.application.dto.SettleReport;
import com.growmighty.lectures.firstday.settlement.domain.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [Step4-4] 파티셔닝 통합 테스트.
 *
 * <p>핵심 검증: 워커마다 <b>겹치지 않는</b> id 범위를 전용 Reader 로 받아 병렬로 처리해도
 * 정확히 전체 건수만큼, 중복 0 으로 정산된다. 멱등 재실행도 안전하다.
 *
 * <p>chunk-size 를 10 으로 좁혀 파티션마다 여러 chunk 로 쪼개지게 한다(병렬성을 실제로 태운다).
 * 처리량(4-2 멀티스레드 대비 확장성)과 재시작 보존은 하드웨어/타이밍에 의존하는 성능 특성이라
 * 단위 테스트가 아니라 라이브 세션의 측정으로 보여준다(여기선 정확성·멱등성만 단정한다).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "settlement.batch.chunk-size=10",
        "spring.jpa.show-sql=false"
})
class SettlementPartitionIntegrationTest {

    private static final int TOTAL = 200;

    @Autowired
    private SettlementBatchService batchService;
    @Autowired
    private SettlementRepository settlementRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        settlementRepository.deleteAll();
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM payments");
        for (long id = 1; id <= TOTAL; id++) {
            BigDecimal amount = BigDecimal.valueOf(1_000 + id * 37 % 99_000);
            jdbc.update("INSERT INTO payments (id, amount, status) VALUES (?, ?, 'PAID')", id, amount);
            jdbc.update("INSERT INTO orders (id, user_id, payment_id, items_amount, shipping_fee, total_amount, status) "
                    + "VALUES (?, ?, ?, ?, 0, ?, 'PAID')", id, id % 1000 + 1, id, amount, amount);
        }
    }

    @Test
    @DisplayName("파티셔닝: 범위를 4개로 나눠 병렬 처리해도 정확히 전체 건수, 중복 0")
    void partitioned_run_settles_all_without_duplicates() {
        SettleReport report = batchService.runPartitioned(4);

        assertThat(report.status()).isEqualTo("COMPLETED");
        assertThat(report.readCount()).isEqualTo(TOTAL);     // 워커 합산 = 전체
        assertThat(report.settledCount()).isEqualTo(TOTAL);  // 전부 정산
        assertThat(settlementRepository.count()).isEqualTo(TOTAL);
        assertThat(distinctOrderIds()).isEqualTo(TOTAL);     // 겹침 없음
    }

    @Test
    @DisplayName("파티셔닝도 멱등: 다시 돌리면 이미 정산된 건은 스킵하고 중복을 만들지 않는다")
    void partitioned_run_is_idempotent() {
        batchService.runPartitioned(4);

        SettleReport second = batchService.runPartitioned(4);
        assertThat(second.status()).isEqualTo("COMPLETED");
        assertThat(second.settledCount()).isZero();          // 새로 만든 정산 없음
        assertThat(second.skippedCount()).isEqualTo(TOTAL);  // 전부 멱등 스킵

        assertThat(settlementRepository.count()).isEqualTo(TOTAL);
        assertThat(distinctOrderIds()).isEqualTo(TOTAL);
    }

    @Test
    @DisplayName("정산 대상이 없으면(빈 파티션) 깨지지 않고 0건으로 정상 종료한다")
    void partitioned_run_handles_no_target() {
        jdbc.update("DELETE FROM orders"); // 대상 주문 제거 → Partitioner 가 빈 범위 파티션 1개 생성

        SettleReport report = batchService.runPartitioned(4);

        assertThat(report.status()).isEqualTo("COMPLETED");
        assertThat(report.settledCount()).isZero();
        assertThat(settlementRepository.count()).isZero();
    }

    private long distinctOrderIds() {
        return jdbc.queryForObject("SELECT COUNT(DISTINCT order_id) FROM settlements", Long.class);
    }
}
