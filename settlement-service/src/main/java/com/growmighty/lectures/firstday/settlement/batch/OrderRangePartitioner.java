package com.growmighty.lectures.firstday.settlement.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * [Step4-4] 정산 대상(PAID 주문)의 PK(id) 범위를 {@code gridSize} 개로 잘게 나누는 마스터.
 *
 * <p>파티셔닝의 핵심은 "데이터 구역을 미리 쪼개서, 워커마다 <b>겹치지 않는</b> 전용 범위를 통째로
 * 넘기는 것" 이다. 각 파티션의 범위(minId~maxId)를 그 파티션 전용 {@link ExecutionContext}
 * (= 1부에서 본 '어디까지 했는지 메모지')에 담아 넘기면, 워커 Step 의 Reader 가 그 범위만 읽는다.
 *
 * <pre>
 *                     ┌─ partition0 : id   1 ~ 250,000
 * OrderRangePartitioner ─┼─ partition1 : id 250,001 ~ 500,000
 *   전체 범위를 4등분    ├─ partition2 : id 500,001 ~ 750,000
 *                       └─ partition3 : id 750,001 ~ 1,000,000
 * </pre>
 *
 * <p>워커마다 <b>전용 입구(전용 Reader)</b>가 생기므로, Multi-threaded Step 의 "Reader 하나를 공유"
 * (4-2 의 깔때기 병목)가 사라진다. 또 범위가 겹치지 않아 같은 주문이 두 워커에 들어갈 수 없으니
 * 이중정산도 구조적으로 불가능하다.
 *
 * <p>시드 데이터는 id 가 1..N 으로 촘촘해 단순 균등 분할로 충분하다. 실데이터처럼 id 가
 * 듬성듬성하면 파티션별 건수가 들쭉날쭉할 수 있는데(데이터 스큐), 그땐 건수 기반 분할 등을 쓴다.
 */
@Slf4j
@RequiredArgsConstructor
public class OrderRangePartitioner implements Partitioner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Long min = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM orders WHERE status = 'PAID'", Long.class);
        Long max = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM orders WHERE status = 'PAID'", Long.class);

        Map<String, ExecutionContext> partitions = new HashMap<>();

        // 대상이 없으면 빈 범위 파티션 1개 (minId > maxId → Reader 가 아무것도 못 읽음)
        if (min == null || max == null) {
            ExecutionContext empty = new ExecutionContext();
            empty.putLong("minId", 1L);
            empty.putLong("maxId", 0L);
            partitions.put("partition0", empty);
            log.warn("[PARTITION] 정산 대상 주문이 없습니다. 빈 파티션 1개 생성");
            return partitions;
        }

        long total = max - min + 1;
        long rangeSize = (long) Math.ceil((double) total / gridSize); // 파티션당 id 폭

        long start = min;
        int index = 0;
        while (start <= max) {
            long end = Math.min(start + rangeSize - 1, max);

            ExecutionContext context = new ExecutionContext();
            context.putLong("minId", start);
            context.putLong("maxId", end);
            partitions.put("partition" + index, context);
            log.warn("[PARTITION] partition{} → id {} ~ {}", index, start, end);

            start = end + 1;
            index++;
        }

        log.warn("[PARTITION] 전체 id {}~{} 를 {}개 파티션으로 분할 (요청 gridSize={})",
                min, max, partitions.size(), gridSize);
        return partitions;
    }
}
