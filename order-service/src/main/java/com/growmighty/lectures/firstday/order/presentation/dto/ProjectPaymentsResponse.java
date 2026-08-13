package com.growmighty.lectures.firstday.order.presentation.dto;

import com.growmighty.lectures.firstday.order.application.dto.ProjectPaymentsView;

import java.math.BigDecimal;
import java.util.List;

public record ProjectPaymentsResponse(List<ProjectPayment> projects) {

    public static ProjectPaymentsResponse from(ProjectPaymentsView view) {
        return new ProjectPaymentsResponse(view.projects().stream()
                .map(ProjectPayment::from)
                .toList());
    }

    public record ProjectPayment(Long projectId, List<OrderPayment> orders) {

        private static ProjectPayment from(ProjectPaymentsView.ProjectPayment project) {
            return new ProjectPayment(project.projectId(), project.orders().stream()
                    .map(OrderPayment::from)
                    .toList());
        }
    }

    public record OrderPayment(Long orderId, String pgOrderId, BigDecimal paymentAmount, String orderStatus) {

        private static OrderPayment from(ProjectPaymentsView.OrderPayment order) {
            return new OrderPayment(order.orderId(), order.pgOrderId(), order.paymentAmount(), order.orderStatus());
        }
    }
}
