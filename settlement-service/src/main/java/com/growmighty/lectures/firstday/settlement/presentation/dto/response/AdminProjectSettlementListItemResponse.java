package com.growmighty.lectures.firstday.settlement.presentation.dto.response;

import com.growmighty.lectures.firstday.settlement.application.query.AdminSettlementEntry;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectCancellationReason;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record AdminProjectSettlementListItemResponse(
        AdminSettlementEntry.Type type,
        Long projectId,
        String projectName,
        Long refundRequestId,
        PayoutResponse payout,
        RefundResponse refund,
        RegistrationPendingResponse registrationPending
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static AdminProjectSettlementListItemResponse from(AdminSettlementEntry entry) {
        return switch (entry.type()) {
            case PAYOUT -> new AdminProjectSettlementListItemResponse(
                    entry.type(),
                    entry.projectId(),
                    entry.projectName(),
                    null,
                    PayoutResponse.from(entry.payout()),
                    null,
                    null
            );
            case REFUND -> new AdminProjectSettlementListItemResponse(
                    entry.type(),
                    entry.projectId(),
                    entry.projectName(),
                    entry.refundRequestId(),
                    null,
                    RefundResponse.from(entry.refund()),
                    null
            );
            case REGISTRATION_PENDING -> new AdminProjectSettlementListItemResponse(
                    entry.type(),
                    entry.projectId(),
                    entry.projectName(),
                    null,
                    null,
                    null,
                    RegistrationPendingResponse.from(entry.registrationPending())
            );
        };
    }

    public record PayoutResponse(
            Long settlementId,
            Long creatorId,
            BigDecimal settlementBaseAmount,
            BigDecimal creatorPayoutAmount,
            PayoutStatus status,
            OffsetDateTime confirmedAt,
            LocalDate scheduledDate
    ) {

        private static PayoutResponse from(AdminSettlementEntry.Payout payout) {
            return new PayoutResponse(
                    payout.settlementId(),
                    payout.creatorId(),
                    payout.settlementBaseAmount().amount(),
                    payout.creatorPayoutAmount().amount(),
                    payout.status(),
                    payout.confirmedAt().atZone(SEOUL).toOffsetDateTime(),
                    payout.scheduledDate()
            );
        }
    }

    public record RefundResponse(
            ProjectCancellationReason reason,
            OffsetDateTime requestedAt,
            AdminSettlementEntry.RefundStatus refundStatus,
            OffsetDateTime paymentResultAt,
            int paymentCount
    ) {

        private static RefundResponse from(AdminSettlementEntry.Refund refund) {
            return new RefundResponse(
                    refund.reason(),
                    refund.requestedAt().atZone(SEOUL).toOffsetDateTime(),
                    refund.refundStatus(),
                    toSeoul(refund.paymentResultAt()),
                    refund.paymentCount()
            );
        }
    }

    public record RegistrationPendingResponse(
            Long settlementId,
            Long creatorId,
            BigDecimal settlementBaseAmount,
            BigDecimal creatorPayoutAmount,
            OffsetDateTime confirmedAt
    ) {

        private static RegistrationPendingResponse from(AdminSettlementEntry.RegistrationPending registrationPending) {
            return new RegistrationPendingResponse(
                    registrationPending.settlementId(),
                    registrationPending.creatorId(),
                    registrationPending.settlementBaseAmount().amount(),
                    registrationPending.creatorPayoutAmount().amount(),
                    registrationPending.confirmedAt().atZone(SEOUL).toOffsetDateTime()
            );
        }
    }

    private static OffsetDateTime toSeoul(java.time.Instant instant) {
        return instant == null ? null : instant.atZone(SEOUL).toOffsetDateTime();
    }
}
