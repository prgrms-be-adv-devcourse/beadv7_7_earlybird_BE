package com.growmighty.lectures.firstday.settlement.batch;

import com.growmighty.lectures.firstday.settlement.read.Order;
import com.growmighty.lectures.firstday.settlement.read.OrderStatus;
import com.growmighty.lectures.firstday.settlement.domain.Settlement;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JpaItemWriter;
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;

/**
 * [Step4] 멀티스레드 Step / 파티셔닝 Job 을 <b>런타임 스레드 수·gridSize 로</b> 조립하는 팩토리.
 *
 * <p>왜 빈으로 고정하지 않고 매번 만드나? 라이브 세션에서 "스레드를 1 → 2 → 4 → 8 로 올려 가며"
 * 처리 시간을 측정하려면(4-2), 병렬도를 요청마다 바꿔 끼울 수 있어야 하기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class SettlementParallelJobFactory {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final OrderToSettlementProcessor settlementProcessor;
    private final JpaItemWriter<Settlement> settlementWriter;
    private final Step settlementWorkerStep;
    private final EntityManagerFactory entityManagerFactory;
    private final JdbcTemplate jdbcTemplate;

    @Value("${settlement.batch.chunk-size:1000}")
    private int chunkSize;

    /**
     * [Step4-1 · 4-2 · 4-3] 전통적 Multi-threaded Step.
     *
     * <p>Chunk 들을 {@code threads} 개 스레드가 <b>각자 통째로</b> 처리한다(read+process+write).
     * Reader 는 <b>표준(thread-safe) {@link JpaPagingItemReader} 하나를 공유</b>한다. 데이터는 안 꼬인다
     * (Batch 6 의 표준 Reader 는 read() 가 내부 Lock 으로 보호됨). 대신 두 가지 한계가 드러난다.
     * <ul>
     *   <li><b>[4-1]</b> 스레드 수 &gt; HikariCP {@code maximumPoolSize} 면 커넥션 고갈 → 타임아웃.</li>
     *   <li><b>[4-2]</b> 읽기가 <b>하나뿐인 Reader(깔때기)</b>로 직렬화되므로, 스레드를 늘려도 처리 시간이
     *       거의 안 준다. (계산대를 늘려도 입구가 회전문 하나면 소용없다.)</li>
     *   <li><b>[4-3]</b> 멀티스레드에서 Reader 상태를 신뢰성 있게 체크포인트할 수 없어 {@code saveState(false)}
     *       가 사실상 강제된다 → Step3 의 "이어서 재시작" 이 사라진다(재시작 시 처음부터 다시 읽음).</li>
     * </ul>
     *
     * <p><b>왜 레거시 {@code chunk(size, txManager)} 빌더인가:</b> Batch 6 의 신형 {@code chunk(size)}
     * ({@code ChunkOrientedStep}) 에 {@code taskExecutor} 를 주면 "읽기는 단일 스레드, 가공만 병렬"
     * (=Async 카드)이라 "여러 스레드가 한 Step 을 도는" 전통적 멀티스레드가 아니다. 그래서 전통적
     * 멀티스레드 Step 을 보여주려고 레거시 빌더({@code TaskExecutorRepeatTemplate})를 쓴다. (Batch 6 비권장)
     */
    @SuppressWarnings("removal") // 전통적 Multi-threaded Step 빌더를 의도적으로 사용(데모). Batch 6 에서 비권장.
    public Job multiThreadedJob(int threads) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("mt-worker-");
        executor.setConcurrencyLimit(threads);

        Step step = new StepBuilder("settlementMultiThreadedStep", jobRepository)
                .<Order, Settlement>chunk(chunkSize, transactionManager) // 레거시 빌더 = 진짜 멀티스레드 Step
                .reader(multiThreadedReader()) // 표준 Reader 하나를 공유 = 읽기 깔때기 + saveState(false)
                .processor(settlementProcessor)
                .writer(settlementWriter)
                .taskExecutor(executor) // ← 한 줄로 멀티스레드. 쉬워 보여서 더 위험하다
                .build();

        return new JobBuilder("settlementMultiThreadedJob", jobRepository)
                .start(step)
                .build();
    }

    /**
     * [Step4-4] 정답 — Partitioning.
     *
     * <p>마스터({@link OrderRangePartitioner})가 id 범위를 {@code gridSize} 개로 쪼개고, 워커 Step
     * ({@code settlementWorkerStep})이 파티션마다 <b>전용 Reader(전용 입구)</b>로 자기 범위만 처리한다.
     * 입구가 여러 개라 4-2 의 깔때기가 사라지고(진짜 병렬), 워커마다 {@code ExecutionContext} 에
     * <b>독립적으로 체크포인트</b>하므로 4-3 에서 잃었던 재시작도 그대로 살아있다.
     */
    public Job partitionedJob(int gridSize) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("part-worker-");
        executor.setConcurrencyLimit(gridSize);

        Step masterStep = new StepBuilder("settlementPartitionedStep", jobRepository)
                .partitioner("settlementWorkerStep", new OrderRangePartitioner(jdbcTemplate))
                .step(settlementWorkerStep)
                .gridSize(gridSize)
                .taskExecutor(executor)
                .build();

        return new JobBuilder("settlementPartitionedJob", jobRepository)
                .start(masterStep)
                .build();
    }

    /**
     * 멀티스레드 Step 이 공유하는 표준 페이징 Reader. 핵심은 {@code saveState(false)} 다 —
     * 여러 스레드가 read() 를 호출하므로 "어디까지 읽었는지" 체크포인트를 신뢰성 있게 남길 수 없다.
     * 그래서 재시작 시 이 Reader 는 <b>처음부터 다시</b> 읽는다(= Step4-3 에서 재시작을 잃는 이유).
     */
    private JpaPagingItemReader<Order> multiThreadedReader() {
        return new JpaPagingItemReaderBuilder<Order>()
                .name("settlementMultiThreadedReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT o FROM Order o WHERE o.status = :status ORDER BY o.id ASC")
                .parameterValues(Map.of("status", OrderStatus.PAID))
                .pageSize(chunkSize)
                .saveState(false) // ← 멀티스레드라 체크포인트 포기 → 재시작('이어서')을 잃는다
                .build();
    }
}
