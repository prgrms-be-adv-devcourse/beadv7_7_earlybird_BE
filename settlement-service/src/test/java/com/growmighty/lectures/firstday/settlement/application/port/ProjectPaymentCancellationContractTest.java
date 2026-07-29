package com.growmighty.lectures.firstday.settlement.application.port;

import static com.growmighty.lectures.firstday.settlement.application.port.ProjectCancellationReason.PROJECT_CANCELLED;
import static com.growmighty.lectures.firstday.settlement.application.port.ProjectCancellationReason.PROJECT_FAILED;
import static com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationStatus.COMPLETED;
import static com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationStatus.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectPaymentCancellationContractTest {

    @Test
    @DisplayName("프로젝트 결제 취소 목록 port가 주문별 완료와 결과 불명확을 구분한다")
    void distinguishesCancellationResultsByOrder() {
        ProjectPaymentCancellationGateway gateway = requests -> List.of(
                new ProjectPaymentCancellationResult(requests.get(0).orderId(), COMPLETED),
                new ProjectPaymentCancellationResult(requests.get(1).orderId(), UNKNOWN)
        );
        List<ProjectPaymentCancellationRequest> requests = List.of(
                new ProjectPaymentCancellationRequest(1_001L, PROJECT_FAILED, "cancel-1001"),
                new ProjectPaymentCancellationRequest(1_002L, PROJECT_CANCELLED, "cancel-1002")
        );

        List<ProjectPaymentCancellationResult> results = gateway.cancel(requests);

        assertThat(results)
                .extracting(
                        ProjectPaymentCancellationResult::orderId,
                        ProjectPaymentCancellationResult::status
                )
                .containsExactly(
                        tuple(1_001L, COMPLETED),
                        tuple(1_002L, UNKNOWN)
                );
    }

    @Test
    @DisplayName("결제 취소 요청은 유효한 주문 식별자와 사유와 멱등키를 요구한다")
    void validatesCancellationRequest() {
        assertThatThrownBy(() -> new ProjectPaymentCancellationRequest(
                0L,
                PROJECT_FAILED,
                "cancel-1001"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectPaymentCancellationRequest(
                1_001L,
                null,
                "cancel-1001"
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProjectPaymentCancellationRequest(
                1_001L,
                PROJECT_FAILED,
                " "
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
