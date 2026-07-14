package com.growmighty.lectures.firstday.settlement.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * 대용량 정산 실습을 위한 시드 데이터 생성기.
 *
 * <p>{@code settlement.seed.enabled=true} 일 때만 동작한다. 부팅 시
 * payments / orders 테이블에 결제 완료(PAID) 주문을 대량으로 적재한다.
 *
 * <pre>
 *   ./gradlew bootRun --args='--settlement.seed.enabled=true --settlement.seed.count=1000000 --spring.jpa.show-sql=false'
 * </pre>
 *
 * <p>구현 포인트(실습 토크 포인트):
 * <ul>
 *   <li>JPA 로 100만 건을 save 하면 영속성 컨텍스트가 터진다 → 여기선 JdbcTemplate 으로 직접 적재</li>
 *   <li>{@code batchUpdate} 로 N 건씩 묶어서 INSERT (1건씩 round-trip 하면 한참 걸린다)</li>
 *   <li>payment 와 order 를 같은 id 로 1:1 매핑 → 이후 조인 실습(orders ⨝ payments)에 사용</li>
 *   <li>금액을 일부러 3% 로 나누어 떨어지지 않게 만들어, 수수료 반올림(1원 무결성) 이슈를 드러낸다</li>
 * </ul>
 */
@Slf4j
@Component
@Order(0) // DataInitializer 보다 먼저 실행되도록 (순서 자체는 무관하지만 로그 가독성)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "settlement.seed.enabled", havingValue = "true")
public class SettlementDataSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Value("${settlement.seed.count:1000000}")
    private long count;

    @Value("${settlement.seed.batch-size:10000}")
    private int batchSize;

    @Override
    public void run(String... args) {

        log.warn("[SEED] 대용량 시드 시작: count={}, batchSize={}", count, batchSize);
        long startedAt = System.currentTimeMillis();

        long inserted = 0;
        while (inserted < count) {
            final long base = inserted;
            final int rows = (int) Math.min(batchSize, count - inserted);

            // 1) 결제 데이터
            jdbcTemplate.batchUpdate(
                    "INSERT INTO payments (id, amount, status) VALUES (?, ?, ?)",
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                            long id = base + i + 1;
                            ps.setLong(1, id);
                            ps.setBigDecimal(2, amountOf(id));
                            ps.setString(3, "PAID");
                        }

                        @Override
                        public int getBatchSize() {
                            return rows;
                        }
                    });

            // 2) 주문 데이터 (payment_id = id 로 1:1 매핑, 배송비 0, 전액 상품금액)
            jdbcTemplate.batchUpdate(
                    "INSERT INTO orders (id, user_id, payment_id, items_amount, shipping_fee, total_amount, status) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                            long id = base + i + 1;
                            BigDecimal amount = amountOf(id);
                            ps.setLong(1, id);
                            ps.setLong(2, id % 1000 + 1); // user_id 분산
                            ps.setLong(3, id);              // payment_id
                            ps.setBigDecimal(4, amount);    // items_amount
                            ps.setBigDecimal(5, BigDecimal.ZERO); // shipping_fee
                            ps.setBigDecimal(6, amount);    // total_amount
                            ps.setString(7, "PAID");
                        }

                        @Override
                        public int getBatchSize() {
                            return rows;
                        }
                    });

            inserted += rows;
            if (inserted % (batchSize * 10L) == 0 || inserted == count) {
                log.warn("[SEED] 진행률 {}/{} ({}%)", inserted, count, inserted * 100 / count);
            }
        }

        long elapsed = System.currentTimeMillis() - startedAt;
        log.warn("[SEED] 완료: orders={}건, payments={}건, 소요시간={}ms", count, count, elapsed);
    }

    /**
     * 3% 로 나누어 떨어지지 않는 금액을 만들기 위한 의도적으로 들쭉날쭉한 금액.
     * 1,000 ~ 약 100,000 사이.
     */
    private static BigDecimal amountOf(long id) {
        long won = 1_000 + id * 37 % 99_000;
        return BigDecimal.valueOf(won);
    }
}
