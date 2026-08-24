// TODO(settlement-plan): Add event, reconciliation, and REVIEW_REQUIRED errors; remove legacy HTTP cancellation errors after migration.
package com.growmighty.lectures.firstday.settlement.application.error;

public enum SettlementErrorCode {

    PAYOUT_PROFILE_NOT_READY(
            "S001",
            "창작자 지급 준비가 완료되지 않았습니다."
    ),
    ORDER_PAYMENT_INPUTS_UNAVAILABLE(
            "S002",
            "주문 결제금액을 확인할 수 없습니다."
    ),
    PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE(
            "S003",
            "프로젝트 정산 대상 정보를 확인할 수 없습니다."
    ),
    PROJECT_SETTLEMENT_NOT_FOUND(
            "S004",
            "프로젝트 정산 내역을 찾을 수 없습니다."
    ),
    PROJECT_REFUND_REQUEST_NOT_FOUND(
            "S005",
            "프로젝트 환불 요청 내역을 찾을 수 없습니다."
    ),
    CREATOR_INFORMATION_UNAVAILABLE(
            "S006",
            "창작자 정보를 일시적으로 확인할 수 없습니다."
    ),
    CREATOR_INFORMATION_INVALID(
            "S007",
            "창작자 정보를 확인할 수 없습니다."
    ),
    SELLER_REGISTRATION_REJECTED(
            "S008",
            "토스 셀러 등록이 거절되었습니다."
    ),
    SELLER_REGISTRATION_RESULT_UNKNOWN(
            "S009",
            "토스 셀러 등록 결과를 확인할 수 없습니다."
    ),
    SETTLEMENT_DATA_INCONSISTENT(
            "S500",
            "프로젝트 정산 데이터가 일치하지 않습니다."
    );

    private final String code;
    private final String message;

    SettlementErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
