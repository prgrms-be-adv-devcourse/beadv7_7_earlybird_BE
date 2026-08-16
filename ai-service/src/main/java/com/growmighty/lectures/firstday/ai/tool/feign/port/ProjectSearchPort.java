package com.growmighty.lectures.firstday.ai.tool.feign.port;

import com.growmighty.lectures.firstday.ai.tool.feign.port.dto.ProjectSearchResult;

import java.util.List;

/**
 * ai-service는 project-service의 클래스를 알지 못한다. 오직 이 인터페이스로만 프로젝트 검색 결과를 바라보고,
 * 실제 통신은 infrastructure(httpClient)의 HTTP 클라이언트가 담당한다.
 */
public interface ProjectSearchPort {
    List<ProjectSearchResult> search(String keyword, Long categoryId, String status, String sort);
}
