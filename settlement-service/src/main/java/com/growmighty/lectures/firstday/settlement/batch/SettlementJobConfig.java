package com.growmighty.lectures.firstday.settlement.batch;

import com.growmighty.lectures.firstday.settlement.read.Order;
import com.growmighty.lectures.firstday.settlement.read.OrderStatus;
import com.growmighty.lectures.firstday.settlement.domain.Settlement;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JpaItemWriter;
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;

/**
 * [Step2] 정산 배치 Job - Chunk 지향 처리 파이프라인.
 *
 * <pre>
 *   Reader(주문 페이지로 읽기) → Processor(주문→정산 변환) → Writer(정산 적재)
 *        └─────────────── chunk 크기만큼 모아서 한 트랜잭션 ───────────────┘
 * </pre>
 *
 * <p>핵심: {@link JpaPagingItemReader} 는 페이지 단위로 읽고 매 페이지마다 영속성 컨텍스트를
 * 비우므로(detach), 100만 건이어도 <b>메모리가 chunk 크기만큼만</b> 쓰인다.
 * Step1 의 {@code findAll()}(전량 적재 → OOM)과 정반대.
 *
 * <p>chunk 크기는 {@code settlement.batch.chunk-size} 로 조절하며 메모리/속도 트레이드오프를 관찰한다.
 */
@Configuration
public class SettlementJobConfig {

    public static final String JOB_NAME = "settlementJob";

    @Value("${settlement.batch.chunk-size:1000}")
    private int chunkSize;

    /**
     * [ItemReader] PAID 주문을 id 순으로 페이지 단위 조회.
     * pageSize 를 chunkSize 와 맞춰 "한 페이지 = 한 청크 = 한 트랜잭션" 이 되게 한다.
     *
     * <p>{@code @StepScope}: Step 실행마다 새로 만들어져 페이지 상태가 매번 초기화된다
     * (Job 반복 실행에 안전). 싱글톤이면 컨테이너 종료 시 close() 가 호출돼 경고가 난다.
     */
    @Bean
    @StepScope
    public JpaPagingItemReader<Order> settlementOrderReader(EntityManagerFactory emf) {
        return new JpaPagingItemReaderBuilder<Order>()
                .name("settlementOrderReader")
                .entityManagerFactory(emf)
                .queryString("SELECT o FROM Order o WHERE o.status = :status ORDER BY o.id ASC")
                .parameterValues(Map.of("status", OrderStatus.PAID))
                .pageSize(chunkSize)
                .build();
    }

    /**
     * [ItemWriter] 정산 엔티티를 chunk 단위로 적재.
     * (대용량 INSERT 최적화는 오후 세션에서 JdbcBatchItemWriter / Bulk Insert 로 다룬다.)
     */
    @Bean
    public JpaItemWriter<Settlement> settlementWriter(EntityManagerFactory emf) {
        return new JpaItemWriterBuilder<Settlement>()
                .entityManagerFactory(emf)
                .build();
    }

    @Bean
    public Step settlementStep(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               JpaPagingItemReader<Order> settlementOrderReader,
                               OrderToSettlementProcessor settlementProcessor,
                               JpaItemWriter<Settlement> settlementWriter) {
        return new StepBuilder("settlementStep", jobRepository)
                .<Order, Settlement>chunk(chunkSize)
                .reader(settlementOrderReader)
                .processor(settlementProcessor)
                .writer(settlementWriter)
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    public Job settlementJob(JobRepository jobRepository, Step settlementStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(settlementStep)
                .build();
    }
}
