package com.growmighty.lectures.firstday.common.kafka;

/**
 * 카프카 토픽 이름을 한곳에서 관리한다.
 * 토픽 이름 규칙: {@code <도메인>.<이벤트/커맨드>.<버전>}
 * 버전을 붙여두면 나중에 메시지 스키마가 바뀌어도(v2) 기존 컨슈머를 깨뜨리지 않고
 * 점진적으로 이전할 수 있다.
 */
public final class KafkaTopics {

    /** project-service → settlement-service : 프로젝트 상태값 변경 이벤트 */
    public static final String PROJECT_STATUS_CHANGED = "project.status-changed.v1";
    /** {@link #PROJECT_STATUS_CHANGED} 처리 실패 격리용 DLT (DeadLetterPublishingRecoverer). */
    public static final String PROJECT_STATUS_CHANGED_DLT = PROJECT_STATUS_CHANGED + "-dlt";

    /** order-service → settlement-service : 주문의 결제 결과 반영 상태 변경 이벤트 */
    public static final String ORDER_PAYMENT_STATUS_CHANGED = "order.payment-status-changed.v1";
    /** {@link #ORDER_PAYMENT_STATUS_CHANGED} 처리 실패 격리용 DLT. */
    public static final String ORDER_PAYMENT_STATUS_CHANGED_DLT = ORDER_PAYMENT_STATUS_CHANGED + "-dlt";

    /** payment-service → order-service : 단일 결제 승인/취소 처리 결과 이벤트 */
    public static final String PAYMENT_SINGLE_RESULT = "payment.single-result.v1";
    /** {@link #PAYMENT_SINGLE_RESULT} 처리 실패 격리용 DLT. */
    public static final String PAYMENT_SINGLE_RESULT_DLT = PAYMENT_SINGLE_RESULT + "-dlt";

    /** settlement-service → payment-service : 결제 일괄 취소 요청 커맨드 */
    public static final String PAYMENT_BULK_CANCEL_COMMAND = "payment.bulk-cancel-command.v1";
    /** {@link #PAYMENT_BULK_CANCEL_COMMAND} 처리 실패 격리용 DLT. */
    public static final String PAYMENT_BULK_CANCEL_COMMAND_DLT = PAYMENT_BULK_CANCEL_COMMAND + "-dlt";

    /** payment-service → order-service : 결제 일괄 취소 처리 결과 이벤트 */
    public static final String PAYMENT_BULK_CANCEL_RESULT = "payment.bulk-cancel-result.v1";
    /** {@link #PAYMENT_BULK_CANCEL_RESULT} 처리 실패 격리용 DLT. */
    public static final String PAYMENT_BULK_CANCEL_RESULT_DLT = PAYMENT_BULK_CANCEL_RESULT + "-dlt";

    private KafkaTopics() {
    }
}
