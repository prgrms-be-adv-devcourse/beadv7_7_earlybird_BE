package com.growmighty.lectures.firstday.file.application.port;

/**
 * 프로젝트 소유권 확인 계약(Port). file-service는 project-service의 클래스를 알지 못한다.
 * 실제 통신은 infrastructure의 HTTP 클라이언트가 담당한다.
 */
public interface ProjectPort {
    Long getCreatorId(Long projectId);
}
