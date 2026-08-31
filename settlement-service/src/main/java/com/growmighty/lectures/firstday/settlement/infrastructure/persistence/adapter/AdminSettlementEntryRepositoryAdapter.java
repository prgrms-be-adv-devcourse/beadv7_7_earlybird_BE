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
                join project_outcome_facts outcome
                    on outcome.project_id = settlement.project_id
                    and outcome.outcome = 'SUCCEEDED'
                left join payout_attempts attempt on attempt.id = obligation.successful_attempt_id

                union all

                select
                    case profile.status
                        when 'PAYOUT_READY' then 'PAYOUT_PENDING'
                        else profile.status
                    end as entry_type,
                    settlement.project_id,
                    outcome.project_name,
                    null as refund_request_id,
                    settlement.confirmed_at as published_at,
                    null as processed_at,
                    settlement.id as settlement_id,
                    settlement.creator_id,
                    settlement.base_amount,
                    settlement.creator_payout_amount,
                    null as payout_status,
                    settlement.confirmed_at,
                    null as scheduled_date,
                    null as refund_reason,
                    null as refund_published_at,
                    null as payment_result_status,
                    null as payment_count
                from project_settlements settlement
                join creator_payout_profiles profile
                    on profile.creator_id = settlement.creator_id
                join project_outcome_facts outcome
                    on outcome.project_id = settlement.project_id
                    and outcome.outcome = 'SUCCEEDED'
                left join payout_obligations obligation on obligation.settlement_id = settlement.id
                where obligation.settlement_id is null

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
                join project_outcome_facts outcome
                    on outcome.project_id = refund_request.project_id
                    and outcome.outcome in ('FAILED', 'CANCELLED')

                union all

                select
                    case
                        when outcome.outcome = 'SUCCEEDED' and exists (
                            select 1
                            from order_payment_facts payment
                            where payment.project_id = outcome.project_id
                              and payment.status = 'COMPLETED'
                              and payment.reconciliation_status = 'REVIEW_REQUIRED'
                        ) then 'RECONCILIATION_REVIEW_REQUIRED'
                        when outcome.outcome = 'SUCCEEDED' then 'SETTLEMENT_PENDING'
                        else 'REFUND_PENDING'
                    end as entry_type,
                    outcome.project_id,
                    outcome.project_name,
                    null as refund_request_id,
                    outcome.occurred_at as published_at,
                    null as processed_at,
                    null as settlement_id,
                    null as creator_id,
                    null as base_amount,
                    null as creator_payout_amount,
                    null as payout_status,
                    null as confirmed_at,
                    null as scheduled_date,
                    null as refund_reason,
                    null as refund_published_at,
                    null as payment_result_status,
                    null as payment_count
                from project_outcome_facts outcome
                where (
                    outcome.outcome = 'SUCCEEDED'
                    and not exists (
                        select 1 from project_settlements settlement
                        where settlement.project_id = outcome.project_id
                    )
                ) or (
                    outcome.outcome in ('FAILED', 'CANCELLED')
                    and not exists (
                        select 1 from project_refund_requested_outbox refund_request
                        where refund_request.project_id = outcome.project_id
                    )
                )
            ) entries
            order by
                case when :sort = 'NAME' then project_name end asc,
                case when :sort = 'PUBLISHED_AT' then published_at end desc,
                case when :sort = 'PROCESSED_AT' then processed_at end desc,
                entry_type asc,
                project_id asc,
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
        return switch ((String) row[0]) {
            case "PAYOUT" -> payout(row);
            case "REGISTRATION_PENDING" -> registrationPending(row);
            case "REFUND" -> refund(row);
            case "PAYOUT_PENDING", "APPROVAL_REQUIRED", "KYC_REQUIRED", "PAYOUT_UNAVAILABLE" -> pendingPayout(row);
            case "RECONCILIATION_REVIEW_REQUIRED", "SETTLEMENT_PENDING", "REFUND_PENDING" -> pending(row);
            default -> throw new IllegalStateException("알 수 없는 관리자 정산 항목 유형입니다.");
        };
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
                null,
                null,
                null
        );
    }

    private static AdminSettlementEntry registrationPending(Object[] row) {
        LocalDateTime confirmedAt = localDateTime(row[11]);
        return new AdminSettlementEntry(
                AdminSettlementEntry.Type.REGISTRATION_PENDING,
                longValue(row[1]),
                (String) row[2],
                null,
                atSeoul(confirmedAt),
                null,
                null,
                null,
                new AdminSettlementEntry.RegistrationPending(
                        longValue(row[6]),
                        longValue(row[7]),
                        Money.wons(decimal(row[8])),
                        Money.wons(decimal(row[9])),
                        confirmedAt
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
                longValue(row[3]),
                publishedAt,
                paymentResultAt,
                null,
                new AdminSettlementEntry.Refund(
                        ProjectCancellationReason.valueOf((String) row[13]),
                        publishedAt,
                        AdminSettlementEntry.RefundStatus.of(row[14] != null, (String) row[15]),
                        paymentResultAt,
                        ((Number) row[16]).intValue()
                ),
                null,
                null
        );
    }

    private static AdminSettlementEntry pendingPayout(Object[] row) {
        LocalDateTime confirmedAt = localDateTime(row[11]);
        return new AdminSettlementEntry(
                AdminSettlementEntry.Type.valueOf((String) row[0]),
                longValue(row[1]),
                (String) row[2],
                null,
                atSeoul(confirmedAt),
                null,
                null,
                null,
                null,
                new AdminSettlementEntry.PendingPayout(
                        longValue(row[6]),
                        longValue(row[7]),
                        Money.wons(decimal(row[8])),
                        Money.wons(decimal(row[9])),
                        confirmedAt
                )
        );
    }

    private static AdminSettlementEntry pending(Object[] row) {
        return new AdminSettlementEntry(
                AdminSettlementEntry.Type.valueOf((String) row[0]),
                longValue(row[1]),
                (String) row[2],
                null,
                instant(row[4]),
                null,
                null,
                null,
                null,
                null
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
