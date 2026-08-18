package com.growmighty.lectures.firstday.project.project.application.port;

/**
 * 프로젝트가 소유한 파일(썸네일 등) 정리 계약(Port). project 는 file-service 의 클래스를 알지 못한다.
 */
public interface FilePort {
    void deleteProjectFiles(Long projectId);
}
