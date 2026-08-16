package com.growmighty.lectures.firstday.settlement.application.port.order;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import java.util.HashSet;
import java.util.List;

public record OrderPaymentRecovery(List<ProjectPayments> projects) {

    public OrderPaymentRecovery {
        projects = List.copyOf(projects);
        if (new HashSet<>(projects.stream().map(ProjectPayments::projectId).toList()).size()
                != projects.size()) {
            throw new IllegalArgumentException("복구 응답의 프로젝트 식별자는 중복될 수 없습니다.");
        }

        List<OrderPayment> payments = projects.stream()
                .flatMap(project -> project.orders().stream())
                .toList();
        if (new HashSet<>(payments.stream().map(OrderPayment::orderId).toList()).size() != payments.size()
                || new HashSet<>(payments.stream().map(OrderPayment::pgOrderId).toList()).size() != payments.size()) {
            throw new IllegalArgumentException("복구 응답의 주문 식별자는 중복될 수 없습니다.");
        }
    }

    public record ProjectPayments(Long projectId, List<OrderPayment> orders) {

        public ProjectPayments {
            if (projectId == null || projectId <= 0) {
                throw new IllegalArgumentException("프로젝트 식별자는 양수여야 합니다.");
            }
            orders = List.copyOf(orders);
        }
    }

    public record OrderPayment(Long orderId, String pgOrderId, Money paymentAmount, Status orderStatus) {

        public enum Status {
            PAID,
            CANCELLED
        }

        public OrderPayment {
            if (orderId == null || orderId <= 0) {
                throw new IllegalArgumentException("주문 식별자는 양수여야 합니다.");
            }
            if (pgOrderId == null || pgOrderId.isBlank()) {
                throw new IllegalArgumentException("PG 정산 식별자는 필수입니다.");
            }
            if (paymentAmount == null || paymentAmount.amount().signum() <= 0) {
                throw new IllegalArgumentException("주문 결제금액은 0원보다 커야 합니다.");
            }
            if (orderStatus == null) {
                throw new IllegalArgumentException("주문 결제 상태는 필수입니다.");
            }
        }
    }
}
