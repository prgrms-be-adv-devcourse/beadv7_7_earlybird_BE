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
 * [Step3] 멱등성/재시작 통합 테스트.
 *
 * <p>chunk-size 를 10 으로 좁혀, 주문 100건이 10개 chunk 로 나뉘게 한다.
 * 그래야 "50% 지점 실패 → 직전 5 chunk(50건)는 커밋, 6번째 chunk 는 롤백" 이 깔끔히 재현된다.
 * (운영 기본값 1000 이면 100건이 단일 chunk 라 50%가 통째로 롤백돼 데모가 안 된다.)
 */
@SpringBootTest
@TestPropertySource(properties = {
        "settlement.batch.chunk-size=10",
        "spring.jpa.show-sql=false"
})
class SettlementRestartIntegrationTest extends MySqlIntegrationTestSupport {

    private static final int TOTAL = 100;

    @Autowired
    private SettlementBatchService batchService;
    @Autowired
    private SettlementRepository settlementRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        // 깨끗한 출발점: 정산/주문/결제 비우고 PAID 주문 100건 적재 (payment_id = id 로 1:1)
        settlementRepository.deleteAll();
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM payments");
        for (long id = 1; id <= TOTAL; id++) {
            BigDecimal amount = BigDecimal.valueOf(1_000 + id * 37 % 99_000);
            jdbc.update("INSERT INTO payments (id, amount, status, created_at, updated_at) VALUES (?, ?, 'PAID', NOW(), NOW())", id, amount);
            jdbc.update("INSERT INTO orders (id, user_id, payment_id, items_amount, shipping_fee, total_amount, status, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, 0, ?, 'PAID', NOW(), NOW())", id, id % 1000 + 1, id, amount, amount);
        }
    }

    @Test
    @DisplayName("멱등성: 50%에서 실패해도 다시 실행하면 이미 정산된 건은 건너뛰고 나머지만 처리해 총 100건")
    void idempotent_rerun_skips_already_settled() {
        // 1차: 50%에서 강제 실패 (새 인스턴스)
        SettleReport failed = batchService.runFailing(0.5);
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(settlementRepository.count()).isEqualTo(50); // 직전 5 chunk 만 커밋

        // 2차: 정상 재실행 (새 인스턴스) → 이미 정산된 50건은 스킵, 남은 50건만 정산
        SettleReport recovered = batchService.run();
        assertThat(recovered.status()).isEqualTo("COMPLETED");
        assertThat(recovered.skippedCount()).isEqualTo(50); // 멱등 스킵된 건수
        assertThat(recovered.settledCount()).isEqualTo(50); // 새로 정산한 건수

        // 최종: 정확히 100건, 중복 0 (order_id 유니크 + existsByOrderId)
        assertThat(settlementRepository.count()).isEqualTo(TOTAL);
        assertThat(distinctOrderIds()).isEqualTo(TOTAL);
    }

    @Test
    @DisplayName("재시작: 같은 runId 로 다시 실행하면 실패 지점부터 이어서 처리해 총 100건")
    void native_restart_resumes_from_checkpoint() {
        // 1차: 같은 runId 로 50%에서 실패
        SettleReport failed = batchService.runRestartable(1L, 0.5);
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(settlementRepository.count()).isEqualTo(50);

        // 2차: 같은 runId, 장애 해제 → Spring Batch 가 실패한 인스턴스를 이어서 재개
        SettleReport resumed = batchService.runRestartable(1L, null);
        assertThat(resumed.status()).isEqualTo("COMPLETED");

        // 최종: 정확히 100건, 중복 0
        assertThat(settlementRepository.count()).isEqualTo(TOTAL);
        assertThat(distinctOrderIds()).isEqualTo(TOTAL);
    }

    @Test
    @DisplayName("정상 실행을 두 번 해도(멱등) 중복 정산이 생기지 않는다")
    void run_twice_is_idempotent() {
        SettleReport first = batchService.run();
        assertThat(first.status()).isEqualTo("COMPLETED");
        assertThat(first.settledCount()).isEqualTo(TOTAL);

        SettleReport second = batchService.run();
        assertThat(second.status()).isEqualTo("COMPLETED");
        assertThat(second.settledCount()).isZero();      // 새로 만든 건 없음
        assertThat(second.skippedCount()).isEqualTo(TOTAL); // 전부 스킵

        assertThat(settlementRepository.count()).isEqualTo(TOTAL);
    }

    private long distinctOrderIds() {
        return jdbc.queryForObject("SELECT COUNT(DISTINCT order_id) FROM settlements", Long.class);
    }
}
