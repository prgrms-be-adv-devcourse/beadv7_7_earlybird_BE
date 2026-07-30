package com.growmighty.lectures.firstday.settlement.infrastructure.client.order;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.ORDER_PAYMENT_INPUTS_UNAVAILABLE;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.OrderPayment;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOrderReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOrders;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public final class ProjectOrderHttpReader implements ProjectOrderReader {

    static final String PROJECT_ORDERS_PATH = "/internal/v1/orders/by-projects/query";
    private static final int MAX_PROJECTS_PER_REQUEST = 100;

    private final RestClient restClient;

    public ProjectOrderHttpReader(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "Order HTTP 클라이언트는 필수입니다.");
    }

    @Override
    public List<ProjectOrders> findProjectOrders(Set<Long> projectIds) {
        ProjectOrdersRequest request = request(projectIds);
        ProjectOrdersEnvelope response;
        try {
            response = restClient.post()
                    .uri(PROJECT_ORDERS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ProjectOrdersEnvelope.class);
        } catch (RestClientException exception) {
            throw new SettlementException(ORDER_PAYMENT_INPUTS_UNAVAILABLE, exception);
        }

        return toProjectOrders(response);
    }

    private static ProjectOrdersRequest request(Set<Long> projectIds) {
        try {
            Set<Long> copiedProjectIds = Set.copyOf(projectIds);
            if (copiedProjectIds.isEmpty()
                    || copiedProjectIds.size() > MAX_PROJECTS_PER_REQUEST
                    || copiedProjectIds.stream().anyMatch(projectId -> projectId <= 0)) {
                throw new IllegalArgumentException("Order 조회 프로젝트 식별자가 유효하지 않습니다.");
            }
            return new ProjectOrdersRequest(copiedProjectIds.stream().sorted().toList());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new SettlementException(ORDER_PAYMENT_INPUTS_UNAVAILABLE, exception);
        }
    }

    private static List<ProjectOrders> toProjectOrders(ProjectOrdersEnvelope response) {
        if (response == null
                || !response.success()
                || response.data() == null
                || response.data().projects() == null
                || response.error() != null) {
            throw new SettlementException(ORDER_PAYMENT_INPUTS_UNAVAILABLE);
        }
        try {
            return response.data().projects().stream()
                    .map(ProjectOrderResponse::toProjectOrders)
                    .toList();
        } catch (RuntimeException exception) {
            throw new SettlementException(ORDER_PAYMENT_INPUTS_UNAVAILABLE, exception);
        }
    }

    private record ProjectOrdersRequest(List<Long> projectIds) {
    }

    private record ProjectOrdersEnvelope(
            boolean success,
            ProjectOrdersData data,
            Object error
    ) {
    }

    private record ProjectOrdersData(List<ProjectOrderResponse> projects) {
    }

    private record ProjectOrderResponse(
            Long projectId,
            List<OrderPaymentResponse> orders
    ) {

        private ProjectOrders toProjectOrders() {
            return new ProjectOrders(
                    projectId,
                    orders.stream().map(OrderPaymentResponse::toOrderPayment).toList()
            );
        }
    }

    private record OrderPaymentResponse(
            Long orderId,
            BigDecimal paymentAmount
    ) {

        private OrderPayment toOrderPayment() {
            return new OrderPayment(orderId, Money.wons(paymentAmount));
        }
    }
}
