package com.growmighty.lectures.firstday.project.project.application.port;

/**
 * 프로젝트 삭제 시 order-service에게 "이 프로젝트에 후원(주문) 이력이 있는지" 묻는 계약.
 */
public interface OrderPort {

    boolean hasOrderedReward(Long projectId);
}
