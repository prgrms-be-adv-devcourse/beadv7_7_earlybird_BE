package com.growmighty.lectures.firstday.settlement.infrastructure.client.order;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.ORDER_PAYMENT_INPUTS_UNAVAILABLE;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPaymentRecovery;
import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPaymentRecovery.OrderPayment;
import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPaymentRecovery.ProjectPayments;
import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPaymentRecoveryReader;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public final class OrderPaymentRecoveryHttpReader implements OrderPaymentRecoveryReader {

    static final String PROJECT_PAYMENTS_PATH = "/internal/v1/orders/project-payments";
    private static final int MAX_PROJECTS_PER_REQUEST = 100;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OrderPaymentRecoveryHttpReader(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = Objects.requireNonNull(restClient, "Order HTTP 클라이언트는 필수입니다.");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 객체 변환기는 필수입니다.");
    }

    @Override
    public OrderPaymentRecovery recover(Set<Long> projectIds, YearMonth settlementMonth) {
        ProjectPaymentsRequest request = request(projectIds, settlementMonth);
        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(PROJECT_PAYMENTS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException exception) {
            throw new SettlementException(ORDER_PAYMENT_INPUTS_UNAVAILABLE, exception);
        }
        return toRecovery(responseBody, Set.copyOf(projectIds));
    }

    private static ProjectPaymentsRequest request(Set<Long> projectIds, YearMonth settlementMonth) {
        Set<Long> copiedProjectIds;
        try {
            copiedProjectIds = Set.copyOf(projectIds);
        } catch (NullPointerException exception) {
            throw new IllegalArgumentException("Order 조회 프로젝트 식별자가 유효하지 않습니다.", exception);
        }
        if (copiedProjectIds.isEmpty()
                || copiedProjectIds.size() > MAX_PROJECTS_PER_REQUEST
                || copiedProjectIds.stream().anyMatch(projectId -> projectId == null || projectId <= 0)
                || settlementMonth == null
                || settlementMonth.getYear() != YearMonth.now().getYear()) {
            throw new IllegalArgumentException("Order 복구 조회 요청이 유효하지 않습니다.");
        }
        return new ProjectPaymentsRequest(settlementMonth.getMonthValue());
    }

    private OrderPaymentRecovery toRecovery(String responseBody, Set<Long> requestedProjectIds) {
        ApiResponse<ProjectPaymentsData> response;
        try {
            response = objectMapper.readValue(responseBody, new TypeReference<>() {
            });
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new IllegalArgumentException("Order 복구 응답 형식이 올바르지 않습니다.", exception);
        }
        if (response == null || !response.success() || response.data() == null
                || response.data().projects() == null || response.error() != null) {
            throw new IllegalArgumentException("Order 복구 응답 envelope가 올바르지 않습니다.");
        }

        try {
            List<ProjectPayments> projects = response.data().projects().stream()
                    .map(ProjectPaymentResponse::toProjectPayments)
                    .toList();
            Set<Long> responseProjectIds = projects.stream()
                    .map(ProjectPayments::projectId)
                    .collect(Collectors.toSet());
            if (responseProjectIds.size() != projects.size() || !responseProjectIds.equals(requestedProjectIds)) {
                throw new IllegalArgumentException("Order 복구 응답 프로젝트 집합이 요청과 일치하지 않습니다.");
            }
            return new OrderPaymentRecovery(projects);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Order 복구 응답 데이터가 올바르지 않습니다.", exception);
        }
    }

    private record ProjectPaymentsRequest(Integer projectMonth) {
    }

    private record ProjectPaymentsData(List<ProjectPaymentResponse> projects) {
    }

    private record ProjectPaymentResponse(Long projectId, List<OrderPaymentResponse> orders) {

        private ProjectPayments toProjectPayments() {
            return new ProjectPayments(
                    projectId,
                    orders.stream().map(OrderPaymentResponse::toOrderPayment).toList()
            );
        }
    }

    private record OrderPaymentResponse(
            Long orderId,
            String pgOrderId,
            BigDecimal paymentAmount,
            String orderStatus
    ) {

        private OrderPayment toOrderPayment() {
            return new OrderPayment(
                    orderId,
                    pgOrderId,
                    Money.wons(paymentAmount),
                    OrderPayment.Status.valueOf(orderStatus)
            );
        }
    }
}
