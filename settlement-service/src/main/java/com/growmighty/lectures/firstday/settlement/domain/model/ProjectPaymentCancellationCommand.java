package com.growmighty.lectures.firstday.settlement.domain.model;

import java.util.Objects;

public final class ProjectPaymentCancellationCommand {

    private final Long id;
    private final Long projectId;
    private final Long orderId;
    private final ProjectCancellationReason reason;
    private final String idempotencyKey;
    private ProjectPaymentCancellationCommandStatus status;
    private final Long version;

    private ProjectPaymentCancellationCommand(
            Long id,
            Long projectId,
            Long orderId,
            ProjectCancellationReason reason,
            String idempotencyKey,
            ProjectPaymentCancellationCommandStatus status,
            Long version
    ) {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("결제 취소 명령 식별자는 양수여야 합니다.");
        }
        if (projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("프로젝트 식별자는 양수여야 합니다.");
        }
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("주문 식별자는 양수여야 합니다.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("멱등키는 필수입니다.");
        }
        if ((id == null) != (version == null)) {
            throw new IllegalArgumentException("저장 식별자와 버전은 함께 존재해야 합니다.");
        }
        this.id = id;
        this.projectId = projectId;
        this.orderId = orderId;
        this.reason = Objects.requireNonNull(reason, "결제 취소 사유는 필수입니다.");
        this.idempotencyKey = idempotencyKey;
        this.status = Objects.requireNonNull(status, "결제 취소 명령 상태는 필수입니다.");
        this.version = version;
    }

    public static ProjectPaymentCancellationCommand request(
            Long projectId,
            Long orderId,
            ProjectCancellationReason reason,
            String idempotencyKey
    ) {
        return new ProjectPaymentCancellationCommand(
                null,
                projectId,
                orderId,
                reason,
                idempotencyKey,
                ProjectPaymentCancellationCommandStatus.REQUESTED,
                null
        );
    }

    public static ProjectPaymentCancellationCommand restore(
            Long id,
            Long projectId,
            Long orderId,
            ProjectCancellationReason reason,
            String idempotencyKey,
            ProjectPaymentCancellationCommandStatus status,
            Long version
    ) {
        return new ProjectPaymentCancellationCommand(
                Objects.requireNonNull(id, "결제 취소 명령 식별자는 필수입니다."),
                projectId,
                orderId,
                reason,
                idempotencyKey,
                status,
                Objects.requireNonNull(version, "결제 취소 명령 버전은 필수입니다.")
        );
    }

    public void record(ProjectPaymentCancellationCommandStatus resultStatus) {
        Objects.requireNonNull(resultStatus, "결제 취소 결과는 필수입니다.");
        if (resultStatus == ProjectPaymentCancellationCommandStatus.REQUESTED) {
            throw new IllegalArgumentException("REQUESTED는 Payment 처리 결과가 아닙니다.");
        }
        if (!status.shouldRequestResult()) {
            if (status != resultStatus) {
                throw new IllegalStateException("종료된 결제 취소 명령 결과를 변경할 수 없습니다.");
            }
            return;
        }
        status = resultStatus;
    }

    public Long id() {
        return id;
    }

    public Long projectId() {
        return projectId;
    }

    public Long orderId() {
        return orderId;
    }

    public ProjectCancellationReason reason() {
        return reason;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public ProjectPaymentCancellationCommandStatus status() {
        return status;
    }

    public Long version() {
        return version;
    }
}
