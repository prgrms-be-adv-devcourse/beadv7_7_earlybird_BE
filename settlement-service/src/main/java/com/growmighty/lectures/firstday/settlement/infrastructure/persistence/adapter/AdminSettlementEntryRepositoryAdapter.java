package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter;

import com.growmighty.lectures.firstday.settlement.application.query.AdminSettlementEntry;
import com.growmighty.lectures.firstday.settlement.application.query.AdminSettlementEntryRepository;
import com.growmighty.lectures.firstday.settlement.application.query.AdminSettlementSort;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectCancellationReason;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminSettlementEntryRepositoryAdapter implements AdminSettlementEntryRepository {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static final String ENTRIES_QUERY = """
            select *
            from (
                select
                    'PAYOUT' as entry_type,
                    settlement.project_id,
                    outcome.project_name,
                    null as refund_request_id,
                    settlement.confirmed_at as published_at,
                    attempt.completed_at as processed_at,
                    settlement.id as settlement_id,
                    settlement.creator_id,
                    settlement.base_amount,
                    settlement.creator_payout_amount,
                    obligation.status as payout_status,
                    settlement.confirmed_at,
                    obligation.scheduled_date,
                    null as refund_reason,
                    null as refund_published_at,
                    null as payment_result_status,
                    null as payment_count
                from project_settlements settlement
                join payout_obligations obligation on obligation.settlement_id = settlement.id
                join project_outcome_facts outcome on outcome.project_id = settlement.project_id
                left join payout_attempts attempt on attempt.id = obligation.successful_attempt_id

                union all

                select
                    'REFUND' as entry_type,
                    refund_request.project_id,
                    outcome.project_name,
                    refund_request.event_id as refund_request_id,
                    refund_request.occurred_at as published_at,
                    refund_request.payment_result_at as processed_at,
                    null as settlement_id,
                    null as creator_id,
                    null as base_amount,
                    null as creator_payout_amount,
                    null as payout_status,
                    null as confirmed_at,
                    null as scheduled_date,
                    refund_request.reason as refund_reason,
                    refund_request.published_at as refund_published_at,
                    refund_request.payment_result_status,
                    (select count(*) from project_refund_requested_payments payment
                     where payment.event_id = refund_request.event_id) as payment_count
                from project_refund_requested_outbox refund_request
                join project_outcome_facts outcome on outcome.project_id = refund_request.project_id
            ) entries
            order by
                case when :sort = 'NAME' then project_name end asc,
                case when :sort = 'PUBLISHED_AT' then published_at end desc,
                case when :sort = 'PROCESSED_AT' then processed_at end desc,
                entry_type asc,
                settlement_id asc,
                refund_request_id asc
            """;

    private final EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public List<AdminSettlementEntry> findAll(AdminSettlementSort sort) {
        return ((List<Object[]>) entityManager.createNativeQuery(ENTRIES_QUERY)
                .setParameter("sort", sort.name())
                .getResultList())
                .stream()
                .map(this::toEntry)
                .toList();
    }

    private AdminSettlementEntry toEntry(Object[] row) {
        return "PAYOUT".equals(row[0]) ? payout(row) : refund(row);
    }

    private static AdminSettlementEntry payout(Object[] row) {
        LocalDateTime confirmedAt = localDateTime(row[11]);
        LocalDateTime completedAt = localDateTime(row[5]);
        return new AdminSettlementEntry(
                AdminSettlementEntry.Type.PAYOUT,
                longValue(row[1]),
                (String) row[2],
                null,
                atSeoul(confirmedAt),
                completedAt == null ? null : atSeoul(completedAt),
                new AdminSettlementEntry.Payout(
                        longValue(row[6]),
                        longValue(row[7]),
                        Money.wons(decimal(row[8])),
                        Money.wons(decimal(row[9])),
                        PayoutStatus.valueOf((String) row[10]),
                        confirmedAt,
                        localDate(row[12])
                ),
                null
        );
    }

    private static AdminSettlementEntry refund(Object[] row) {
        Instant publishedAt = instant(row[4]);
        Instant paymentResultAt = instant(row[5]);
        return new AdminSettlementEntry(
                AdminSettlementEntry.Type.REFUND,
                longValue(row[1]),
                (String) row[2],
                (String) row[3],
                publishedAt,
                paymentResultAt,
                null,
                new AdminSettlementEntry.Refund(
                        ProjectCancellationReason.valueOf((String) row[13]),
                        publishedAt,
                        AdminSettlementEntry.RefundStatus.of(row[14] != null, (String) row[15]),
                        paymentResultAt,
                        ((Number) row[16]).intValue()
                )
        );
    }

    private static long longValue(Object value) {
        return ((Number) value).longValue();
    }

    private static BigDecimal decimal(Object value) {
        return (BigDecimal) value;
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        return ((LocalDateTime) value).toInstant(ZoneOffset.UTC);
    }

    private static LocalDateTime localDateTime(Object value) {
        if (value == null) return null;
        return value instanceof Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) value;
    }

    private static LocalDate localDate(Object value) {
        if (value == null) return null;
        return value instanceof java.sql.Date date ? date.toLocalDate() : (LocalDate) value;
    }

    private static Instant atSeoul(LocalDateTime value) {
        return value.atZone(SEOUL).toInstant();
    }
}
