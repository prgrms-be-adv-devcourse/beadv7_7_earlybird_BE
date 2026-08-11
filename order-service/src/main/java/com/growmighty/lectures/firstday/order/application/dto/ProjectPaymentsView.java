package com.growmighty.lectures.firstday.order.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProjectPaymentsView(List<ProjectPayment> projects) {

    public record ProjectPayment(Long projectId, List<OrderPayment> orders) {
    }

    public record OrderPayment(Long orderId, String pgOrderId, BigDecimal paymentAmount) {
    }
}
