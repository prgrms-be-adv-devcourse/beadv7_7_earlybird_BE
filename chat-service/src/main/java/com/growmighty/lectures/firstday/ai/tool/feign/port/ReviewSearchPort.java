package com.growmighty.lectures.firstday.ai.tool.feign.port;

import com.growmighty.lectures.firstday.ai.tool.feign.port.dto.ReviewSearchResult;

import java.util.List;

/**
 * chat-service는 board-service의 클래스를 알지 못한다. 오직 이 인터페이스로만 리뷰 조회 결과를 바라보고,
 * 실제 통신은 infrastructure(httpClient)의 HTTP 클라이언트가 담당한다.
 */
public interface ReviewSearchPort {
    List<ReviewSearchResult> search(Long projectId);
}
