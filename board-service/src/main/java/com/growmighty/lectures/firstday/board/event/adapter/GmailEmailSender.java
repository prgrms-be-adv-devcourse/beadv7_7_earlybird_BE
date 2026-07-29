package com.growmighty.lectures.firstday.board.event.adapter;

import com.growmighty.lectures.firstday.board.event.port.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * EmailSender의 실제 구현체 — Gmail SMTP로 발송한다.
 * JavaMailSender는 spring.mail.host가 설정돼 있으면 Spring Boot가 자동으로 빈을 만들어준다
 * (MailSenderAutoConfiguration) — 직접 SMTP 클라이언트를 짤 필요가 없다.
 * 자격증명(spring.mail.username/password)이 비어있으면 send() 호출 시점에 인증 실패로 예외가 나는데,
 * 이 예외는 호출부인 리스너가 잡아서 로그만 남기고 삼키도록 이미 돼 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GmailEmailSender implements EmailSender {

    private final JavaMailSender mailSender;

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}