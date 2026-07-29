package com.growmighty.lectures.firstday.board.feign.port;

import com.growmighty.lectures.firstday.board.feign.port.dto.UserSnapshot;

/**
 * board-service 는 user-service 의 클래스를 알지 못한다. 오직 이 인터페이스로만 작성자 정보를 바라보고,
 * 실제 통신은 infrastructure 의 HTTP 클라이언트가 담당한다.
 */
public interface UserPort {
    UserSnapshot getUser(Long userId);
}