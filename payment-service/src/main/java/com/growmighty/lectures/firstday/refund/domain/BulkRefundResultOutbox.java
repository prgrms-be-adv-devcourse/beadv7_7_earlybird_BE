package com.growmighty.lectures.firstday.refund.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "bulk_refund_result_outbox",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_bulk_refund_result_outbox_settlement_id",
        columnNames = "settlement_id"
    ),
    indexes = @Index(
        name = "idx_bulk_refund_result_outbox_status_id",
        columnList = "outbox_status, id"
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BulkRefundResultOutbox extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_id", nullable = false)
    private Long settlementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false)
    private BulkRefundResultStatus resultStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BulkRefundResultOutboxStatus outboxStatus;

    @Column(nullable = false)
    private int retryCount;

    private LocalDateTime sentAt;

    private BulkRefundResultOutbox(Long settlementId, BulkRefundResultStatus resultStatus) {
        this.settlementId = settlementId;
        this.resultStatus = resultStatus;
        this.outboxStatus = BulkRefundResultOutboxStatus.PENDING;
    }

    public static BulkRefundResultOutbox pending(Long settlementId, BulkRefundResultStatus resultStatus) {
        return new BulkRefundResultOutbox(settlementId, resultStatus);
    }

    public void markSent() {
        if (outboxStatus == BulkRefundResultOutboxStatus.SENT) {
            return;
        }

        outboxStatus =  BulkRefundResultOutboxStatus.SENT;
        sentAt = LocalDateTime.now();
    }

    public void increaseRetryCount() {
        retryCount++;
    }

}
