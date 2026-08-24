package com.growmighty.lectures.firstday.settlement.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "project_outcome_facts")
public class ProjectOutcomeFact {

    public enum Outcome {
        SUCCEEDED,
        FAILED,
        CANCELLED
    }

    @Id
    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(name = "project_name", nullable = false, updatable = false)
    private String projectName;

    @Column(name = "creator_id", nullable = false, updatable = false)
    private Long creatorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false, length = 20)
    private Outcome outcome;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Version
    private Long version;

    protected ProjectOutcomeFact() {
    }

    private ProjectOutcomeFact(Long projectId, String projectName, Long creatorId, Outcome outcome, Instant occurredAt) {
        this.projectId = projectId;
        this.projectName = projectName;
        this.creatorId = creatorId;
        this.outcome = outcome;
        this.occurredAt = occurredAt;
        validateState();
    }

    public static ProjectOutcomeFact of(
            Long projectId,
            String projectName,
            Long creatorId,
            Outcome outcome,
            Instant occurredAt
    ) {
        return new ProjectOutcomeFact(projectId, projectName, creatorId, outcome, occurredAt);
    }

    public Long projectId() {
        return projectId;
    }

    public String projectName() {
        return projectName;
    }

    public Long creatorId() {
        return creatorId;
    }

    public Outcome outcome() {
        return outcome;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public boolean requiresPayout() {
        return outcome == Outcome.SUCCEEDED;
    }

    public boolean requiresRefund() {
        return outcome == Outcome.FAILED || outcome == Outcome.CANCELLED;
    }

    public List<OrderPaymentFact> refundablePaymentsDueBefore(
            Instant dueBefore,
            List<OrderPaymentFact> payments
    ) {
        Objects.requireNonNull(dueBefore, "환불 기준 시각은 필수입니다.");
        List<OrderPaymentFact> input = List.copyOf(payments);
        if (!requiresRefund() || !occurredAt.isBefore(dueBefore) || input.isEmpty()) {
            return List.of();
        }
        if (input.stream().anyMatch(payment -> payment == null
                || !projectId.equals(payment.projectId())
                || payment.completedAt().isAfter(occurredAt)
                || (payment.cancelledAt() != null && payment.cancelledAt().isAfter(occurredAt)))) {
            return List.of();
        }
        return input.stream()
                .filter(payment -> payment.status() == OrderPaymentFact.Status.COMPLETED)
                .toList();
    }

    @PostLoad
    private void validateState() {
        validatePositive(projectId, "프로젝트 식별자는 양수여야 합니다.");
        if (projectName == null || projectName.isBlank()) {
            throw new IllegalArgumentException("프로젝트명은 필수입니다.");
        }
        validatePositive(creatorId, "창작자 식별자는 양수여야 합니다.");
        Objects.requireNonNull(outcome, "프로젝트 결과는 필수입니다.");
        Objects.requireNonNull(occurredAt, "프로젝트 결과 시각은 필수입니다.");
    }

    private static void validatePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
