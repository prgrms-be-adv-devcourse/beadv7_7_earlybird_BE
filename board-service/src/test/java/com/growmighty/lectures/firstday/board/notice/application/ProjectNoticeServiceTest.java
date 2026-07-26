package com.growmighty.lectures.firstday.board.notice.application;

import com.growmighty.lectures.firstday.board.application.port.UserPort;
import com.growmighty.lectures.firstday.board.application.port.dto.UserSnapshot;
import com.growmighty.lectures.firstday.board.notice.application.dto.ProjectNoticeResult;
import com.growmighty.lectures.firstday.board.notice.application.dto.RegisterProjectNoticeCommand;
import com.growmighty.lectures.firstday.board.notice.domain.ProjectNotice;
import com.growmighty.lectures.firstday.board.notice.domain.ProjectNoticeRepository;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectNoticeServiceTest {

    @Mock
    private ProjectNoticeRepository noticeRepository;
    @Mock
    private UserPort userPort;

    private ProjectNoticeService noticeService;

    @BeforeEach
    void setUp() {
        noticeService = new ProjectNoticeService(noticeRepository, userPort);
    }

    @Test
    @DisplayName("등록 시 authorId로 user-service에서 이름을 조회해 채운다")
    void register_resolvesAuthorNameFromUserPort() {
        when(userPort.getUser(1L)).thenReturn(new UserSnapshot(1L, "홍길동"));
        when(noticeRepository.save(any(ProjectNotice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectNoticeResult result = noticeService.register(
                new RegisterProjectNoticeCommand(10L, 1L, "제목", "내용"));

        assertThat(result.authorName()).isEqualTo("홍길동");
        verify(userPort).getUser(1L);
    }

    @Test
    @DisplayName("user-service에서 작성자 정보를 가져오지 못하면 등록 자체가 실패한다")
    void register_failsHardWhenUserInfoUnavailable() {
        when(userPort.getUser(1L)).thenThrow(new ServiceUnavailableException("사용자 정보를 확인할 수 없습니다."));

        assertThatThrownBy(() -> noticeService.register(new RegisterProjectNoticeCommand(10L, 1L, "제목", "내용")))
                .isInstanceOf(ServiceUnavailableException.class);

        verify(noticeRepository, never()).save(any());
    }
}