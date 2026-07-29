package com.growmighty.lectures.firstday.board.event.port;

/**
 * 이메일 발송 수단에 대한 포트. 지금은 event.adapter.LoggingEmailSender 가 로그로만 대체하고,
 * 실제 Gmail 연동 어댑터는 후속 브랜치에서 추가한다.
 */
public interface EmailSender {
    void send(String to, String subject, String body);
}