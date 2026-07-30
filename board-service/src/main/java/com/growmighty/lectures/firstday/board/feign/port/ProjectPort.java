package com.growmighty.lectures.firstday.board.feign.port;

/**
 * board-service 는 project-service 의 클래스를 알지 못한다. 오직 이 인터페이스로만 프로젝트 존재 여부를 바라보고,
 * 실제 통신은 infrastructure 의 HTTP 클라이언트가 담당한다.
 */
public interface ProjectPort {
    boolean existsProject(Long projectId);

    Long getCreatorUserId(Long projectId);
}