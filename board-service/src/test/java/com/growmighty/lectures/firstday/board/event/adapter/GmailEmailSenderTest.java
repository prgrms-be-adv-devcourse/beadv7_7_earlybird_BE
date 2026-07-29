package com.growmighty.lectures.firstday.board.event.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

// 실제 SMTP 통신(JavaMailSender 내부 동작)은 검증 대상이 아니다 — 그건 이미 검증된 프레임워크 기능이다.
// 여기선 SimpleMailMessage 조립이 맞는지, 발송 실패 시 예외를 그대로 흘려보내는지(리스너가 잡도록)만 본다.
@ExtendWith(MockitoExtension.class)
class GmailEmailSenderTest {

    @Mock
    private JavaMailSender javaMailSender;

    private GmailEmailSender gmailEmailSender;

    @BeforeEach
    void setUp() {
        gmailEmailSender = new GmailEmailSender(javaMailSender);
    }

    @Test
    @DisplayName("to/subject/body를 SimpleMailMessage에 그대로 담아 전달한다")
    void send_buildsMessageCorrectly() {
        gmailEmailSender.send("creator@example.com", "제목", "본문");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly("creator@example.com");
        assertThat(message.getSubject()).isEqualTo("제목");
        assertThat(message.getText()).isEqualTo("본문");
    }

    @Test
    @DisplayName("발송이 실패하면 예외를 삼키지 않고 그대로 전파한다 (리스너가 잡도록)")
    void send_failure_propagates() {
        doThrow(new MailSendException("SMTP 인증 실패")).when(javaMailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> gmailEmailSender.send("creator@example.com", "제목", "본문"))
            .isInstanceOf(MailSendException.class);
    }
}