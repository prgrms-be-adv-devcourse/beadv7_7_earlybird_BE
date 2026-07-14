package com.growmighty.lectures.firstday.settlement.application.dto;

/**
 * 정산 작업 결과 리포트 - "얼마나 처리했고, 얼마나 걸렸고, 메모리는 얼마나 썼나".
 * 배치 필요성("느림 + 메모리")을 수치로 보여주기 위한 DTO.
 *
 * <p>[Step3] 멱등성/재시작 데모를 위해 {@code skippedCount}(이미 정산돼 건너뛴 수)와
 * {@code status}(COMPLETED / FAILED) 를 함께 보여준다.
 */
public record SettleReport(
        long readCount,     // 메모리로 읽어들인 주문 수
        long settledCount,  // 이번 실행에서 실제로 만든 정산 건수 (Writer 로 넘어간 수)
        long skippedCount,  // [Step3] 이미 정산돼 건너뛴(필터링된) 주문 수
        long elapsedMs,     // 소요 시간
        long peakHeapMb,    // 작업 중 피크 힙 사용량
        long maxHeapMb,     // -Xmx 상한
        String status       // [Step3] 작업 결과 상태 (COMPLETED / FAILED / ...)
) {
}
