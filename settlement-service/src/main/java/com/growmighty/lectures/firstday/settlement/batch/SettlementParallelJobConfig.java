package com.growmighty.lectures.firstday.settlement.batch;

import com.growmighty.lectures.firstday.settlement.read.Order;
import com.growmighty.lectures.firstday.settlement.read.OrderStatus;
import com.growmighty.lectures.firstday.settlement.domain.Settlement;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JpaItemWriter;
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;

/**
 * [Step4-4] 파티셔닝의 <b>워커(Worker)</b> 쪽 구성.
 *
 * <p>마스터({@link OrderRangePartitioner})가 나눠 준 범위(minId~maxId)를 받아,
 * <b>그 범위만</b> 읽어 정산하는 Step 이다. 마스터 Step 과 Job 의 조립은 스레드 수/gridSize 를
 * 런타임에 받기 위해 {@link SettlementParallelJobFactory} 에서 동적으로 만든다.
 *
 * <p>{@link #settlementWorkerReader} 가 {@code @StepScope} 인 게 파티셔닝의 핵심이다.
 * 파티션(=워커 Step 실행)마다 Reader 인스턴스가 <b>따로(전용 입구)</b> 생기고, 각자 자기 범위만 읽는다.
 * Multi-threaded Step 처럼 Reader 하나를 공유(깔때기)하지 않으니, 4-2 의 읽기 병목이 사라지고
 * 워커마다 {@code saveState=true} 독립 체크포인트라 4-3 에서 잃었던 재시작도 보존된다.
 */
@Configuration
public class SettlementParallelJobConfig {

    @Value("${settlement.batch.chunk-size:1000}")
    private int chunkSize;

    /**
     * [워커 Reader] 자기 파티션의 id 범위(minId~maxId)에 해당하는 PAID 주문만 페이지로 읽는다.
     *
     * <p>{@code minId/maxId} 는 마스터가 각 파티션의 {@code stepExecutionContext} 에 넣어 준 값이다.
     * {@code @StepScope} 라서 이 SpEL(@code #{stepExecutionContext[...]}) 바인딩이 동작하고,
     * 파티션마다 자기 범위가 주입된 <b>독립 Reader</b>가 만들어진다.
     */
    @Bean
    @StepScope
    public JpaPagingItemReader<Order> settlementWorkerReader(
            EntityManagerFactory emf,
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId) {
        return new JpaPagingItemReaderBuilder<Order>()
                .name("settlementWorkerReader")
                .entityManagerFactory(emf)
                .queryString("SELECT o FROM Order o "
                        + "WHERE o.status = :status AND o.id BETWEEN :minId AND :maxId "
                        + "ORDER BY o.id ASC")
                .parameterValues(Map.of(
                        "status", OrderStatus.PAID,
                        "minId", minId,
                        "maxId", maxId))
                .pageSize(chunkSize)
                .build();
    }

    /**
     * [워커 Step] 받은 범위를 Chunk 지향으로 정산한다. 구조는 Step2 의 단일 Step 과 동일하고,
     * Reader 만 "범위 한정 {@link #settlementWorkerReader}" 로 바뀐 것뿐이다.
     * Processor/Writer 는 Step2·3 의 것을 그대로 재사용한다(멱등 스킵·1원 무결성 유지).
     */
    @Bean
    public Step settlementWorkerStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("settlementWorkerReader") JpaPagingItemReader<Order> settlementWorkerReader,
            OrderToSettlementProcessor settlementProcessor,
            JpaItemWriter<Settlement> settlementWriter) {
        return new StepBuilder("settlementWorkerStep", jobRepository)
                .<Order, Settlement>chunk(chunkSize)
                .reader(settlementWorkerReader)
                .processor(settlementProcessor)
                .writer(settlementWriter)
                .transactionManager(transactionManager)
                .build();
    }
}
