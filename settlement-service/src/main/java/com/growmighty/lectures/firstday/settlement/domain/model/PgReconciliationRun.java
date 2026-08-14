package com.growmighty.lectures.firstday.settlement.domain.model;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Objects;

@Entity
@Table(
        name = "pg_reconciliation_runs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pg_reconciliation_run_active_month",
                columnNames = "active_run_month"
        )
)
public class PgReconciliationRun extends BaseEntity {

    public enum Status {
        RUNNING,
        COMPLETED,
        REVIEW_REQUIRED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_month", nullable = false, updatable = false, length = 7)
    private YearMonth settlementMonth;

    @Column(name = "active_run_month", unique = true, length = 7)
    private String activeRunMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    protected PgReconciliationRun() {
    }

    private PgReconciliationRun(YearMonth settlementMonth, LocalDateTime startedAt) {
        this.settlementMonth = Objects.requireNonNull(settlementMonth, "정산 대상 월은 필수입니다.");
        this.activeRunMonth = settlementMonth.toString();
        this.status = Status.RUNNING;
        this.startedAt = Objects.requireNonNull(startedAt, "대사 실행 시작 시각은 필수입니다.");
        validateState();
    }

    public static PgReconciliationRun start(YearMonth settlementMonth, LocalDateTime startedAt) {
        return new PgReconciliationRun(settlementMonth, startedAt);
    }

    public void complete(LocalDateTime finishedAt) {
        finish(Status.COMPLETED, finishedAt);
    }

    public void requireReview(LocalDateTime finishedAt) {
        finish(Status.REVIEW_REQUIRED, finishedAt);
    }

    public void fail(LocalDateTime finishedAt) {
        finish(Status.FAILED, finishedAt);
    }

    public Long id() {
        return id;
    }

    public YearMonth settlementMonth() {
        return settlementMonth;
    }

    public Status status() {
        return status;
    }

    public LocalDateTime startedAt() {
        return startedAt;
    }

    public LocalDateTime finishedAt() {
        return finishedAt;
    }

    public boolean running() {
        return status == Status.RUNNING;
    }

    @PostLoad
    private void validateState() {
        Objects.requireNonNull(settlementMonth, "정산 대상 월은 필수입니다.");
        Objects.requireNonNull(status, "대사 실행 상태는 필수입니다.");
        Objects.requireNonNull(startedAt, "대사 실행 시작 시각은 필수입니다.");
        if (running()) {
            if (!settlementMonth.toString().equals(activeRunMonth) || finishedAt != null) {
                throw new IllegalArgumentException("실행 중인 대사는 활성 월 키만 가져야 합니다.");
            }
            return;
        }
        if (activeRunMonth != null || finishedAt == null || finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("종료된 대사 실행 상태가 올바르지 않습니다.");
        }
    }

    private void finish(Status status, LocalDateTime finishedAt) {
        if (!running()) {
            throw new IllegalStateException("종료된 대사 실행은 다시 변경할 수 없습니다.");
        }
        this.status = status;
        this.finishedAt = Objects.requireNonNull(finishedAt, "대사 실행 종료 시각은 필수입니다.");
        this.activeRunMonth = null;
        validateState();
    }
}
