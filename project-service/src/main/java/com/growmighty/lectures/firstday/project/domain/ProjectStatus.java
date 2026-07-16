package com.growmighty.lectures.firstday.project.domain;

/**
 * 프로젝트(펀딩 단위) 상태.
 * "펀딩 성공/실패" 대신 "목표 금액 달성/실패"로 명명한다 (팀 용어사전).
 * 배송·제작 진행 상태는 프로젝트 상태와 분리된 별도 상태로 관리한다.
 */
public enum ProjectStatus {
    DRAFT,          // 작성중
    IN_REVIEW,      // 관리자 심사중
    REJECTED,       // 심사 반려
    OPEN,           // 공개 — 펀딩(후원) 진행중
    CLOSED,         // 마감 — 달성 판정 대기
    GOAL_REACHED,   // 목표 금액 달성 → 정산 대상
    GOAL_FAILED     // 목표 금액 실패 → 일괄 환불 대상
}
