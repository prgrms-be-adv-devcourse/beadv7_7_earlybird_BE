package com.growmighty.lectures.firstday.order.infrastructure.client;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.order.application.port.PaymentPort;
import com.growmighty.lectures.firstday.order.application.port.dto.PaymentResult;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.PayBody;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.PaymentApiData;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.PaymentDetailsApiData;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
//import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentHttpClient implements PaymentPort {

    private final PaymentFeignClient paymentFeignClient;

    @Override
    public PaymentResult pay(Long orderId, Long userId, BigDecimal amount) {
        ApiResponse<PaymentApiData> response;
        try {
            response = paymentFeignClient.pay(new PayBody(orderId, userId, amount));
        } catch (FeignException.Conflict e) {
            return PaymentResult.unknown(amount);
        }
        return toPaymentResult(response, amount);
    }

    @Override
    public CancellationResult cancel(Long paymentId, BigDecimal amount) {
        ApiResponse<PaymentDetailsApiData> response = paymentFeignClient.cancel(paymentId);
        if (response == null || !response.success() || response.data() == null
                || response.data().status() == null) {
            return new CancellationResult(PaymentResult.Status.UNKNOWN, amount, paymentId, null);
        }

        PaymentDetailsApiData data = response.data();
        if (!paymentId.equals(data.paymentId())) {
            throw new ServiceUnavailableException("Payment cancellation ID mismatch. paymentId=" + paymentId);
        }
        if (data.amount() == null || amount.compareTo(data.amount()) != 0) {
            throw new ServiceUnavailableException("Payment cancellation amount mismatch. paymentId=" + paymentId);
        }

        PaymentResult.Status status = "CANCELLED".equals(data.status())
                ? PaymentResult.Status.SUCCESS
                : PaymentResult.Status.FAILURE;
        return new CancellationResult(status, data.amount(), data.paymentId(), data.orderId());
    }

    @Override
    public PaymentResult getPaymentResult(Long orderId) {
        ApiResponse<PaymentDetailsApiData> response = paymentFeignClient.getPaymentByOrderId(orderId);
        if (response == null || !response.success() || response.data() == null
                || response.data().status() == null) {
            return new PaymentResult(null, null, null, PaymentResult.Status.UNKNOWN);
        }

        PaymentDetailsApiData data = response.data();
        if (!orderId.equals(data.orderId())) {
            throw new ServiceUnavailableException("Payment order ID mismatch. orderId=" + orderId);
        }
        PaymentResult.Status status = switch (data.status()) {
            case "PAID" -> PaymentResult.Status.SUCCESS;
            case "CANCELLED" -> PaymentResult.Status.CANCELLED;
            case "FAILED" -> PaymentResult.Status.FAILURE;
            case "READY", "CONFIRMING" -> PaymentResult.Status.PENDING;
            default -> PaymentResult.Status.UNKNOWN;
        };
        return new PaymentResult(data.paymentId(), data.pgOrderId(), data.amount(), status);
    }

    private PaymentResult toPaymentResult(ApiResponse<PaymentApiData> response, BigDecimal requestedAmount) {
        if (response == null || !response.success() || response.data() == null || response.data().status() == null) {
            return PaymentResult.unknown(requestedAmount);
        }

        PaymentApiData data = response.data();
        BigDecimal amount = data.amount() != null ? data.amount() : requestedAmount;
        return switch (data.status()) {
            case "PAID" -> PaymentResult.success(data.paymentId(), data.pgOrderId(), amount);
            case "CANCELLED" -> PaymentResult.cancelled(data.paymentId(), data.pgOrderId(), amount);
            case "FAILED" -> PaymentResult.failure(data.pgOrderId(), amount);
            case "READY", "CONFIRMING" -> PaymentResult.pending(data.pgOrderId(), amount);
            default -> PaymentResult.unknown(data.pgOrderId(), amount);
        };
    }
}
