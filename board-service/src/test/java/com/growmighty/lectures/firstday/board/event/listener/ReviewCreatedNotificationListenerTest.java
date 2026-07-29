package com.growmighty.lectures.firstday.board.event.listener;

import com.growmighty.lectures.firstday.board.event.ReviewCreatedEvent;
import com.growmighty.lectures.firstday.board.event.port.EmailSender;
import com.growmighty.lectures.firstday.board.feign.port.ProjectPort;
import com.growmighty.lectures.firstday.board.feign.port.UserPort;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Spring 트랜잭션 동기화(@TransactionalEventListener의 AFTER_COMMIT 타이밍)는 여기서 검증하지 않는다 —
// 그건 프레임워크가 보장하는 부분이고, 여기선 이 리스너가 새로 짠 로직(포트 호출 배선 + 실패 삼키기)만 좁게 검증한다.
@ExtendWith(MockitoExtension.class)
class ReviewCreatedNotificationListenerTest {

    @Mock
    private ProjectPort projectPort;
    @Mock
    private UserPort userPort;
    @Mock
    private EmailSender emailSender;

    private ReviewCreatedNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new ReviewCreatedNotificationListener(projectPort, userPort, emailSender);
    }

    @Test
    @DisplayName("정상 흐름이면 제작자 이메일로 발송한다")
    void notifyCreator_success() {
        ReviewCreatedEvent event = ReviewCreatedEvent.of(1L, 10L, 5L, "홍길동");
        when(projectPort.getCreatorUserId(10L)).thenReturn(99L);
        when(userPort.getUserEmail(99L)).thenReturn("creator@example.com");

        listener.notifyCreator(event);

        verify(emailSender).send(eq("creator@example.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("프로젝트/사용자 조회가 실패해도 예외를 밖으로 전파하지 않고 삼킨다")
    void notifyCreator_portFailure_isSwallowed() {
        ReviewCreatedEvent event = ReviewCreatedEvent.of(1L, 10L, 5L, "홍길동");
        when(projectPort.getCreatorUserId(10L)).thenThrow(new ServiceUnavailableException("프로젝트 정보를 확인할 수 없습니다."));

        assertThatCode(() -> listener.notifyCreator(event)).doesNotThrowAnyException();

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }
}