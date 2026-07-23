package com.growmighty.lectures.firstday.project.project.application;

/**
 * Reward 등 다른 도메인이 프로젝트 상태/소유자만 필요할 때 쓰는 뷰 — Project 엔티티/리포지토리를
 * 직접 넘기지 않고 ProjectService(포트) 경계 안에서만 상태와 creatorId를 노출한다.
 */
public record ProjectStatusView(
        boolean published,
        boolean closed,
        boolean open,
        String status,
        Long creatorId
) {
}
