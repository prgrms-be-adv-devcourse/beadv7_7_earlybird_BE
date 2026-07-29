package com.growmighty.lectures.firstday.board.event.adapter;

import com.growmighty.lectures.firstday.board.event.port.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * EmailSender의 임시 구현체. 실제 Gmail 연동 전까지는 발송 대신 로그로 남긴다.
 * EmailSender 구현체가 하나도 없으면 이 포트를 주입받는 리스너가 빈을 못 찾아 앱이 기동조차 안 되므로,
 * 실제 연동 전에도 최소 하나는 등록해 둔다.
 */
// TODO(팀): Gmail 연동 어댑터로 교체
@Slf4j
@Component
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String body) {
        log.info("[이메일 발송 대체 로그] to={}, subject={}, body={}", to, subject, body);
    }
}