package com.growmighty.lectures.firstday.board.comment.application;

import com.growmighty.lectures.firstday.board.feign.port.ProjectPort;
import com.growmighty.lectures.firstday.board.feign.port.UserPort;
import com.growmighty.lectures.firstday.board.feign.port.dto.UserSnapshot;
import com.growmighty.lectures.firstday.board.comment.application.dto.CommentResult;
import com.growmighty.lectures.firstday.board.comment.application.dto.RegisterCommentCommand;
import com.growmighty.lectures.firstday.board.comment.domain.Comment;
import com.growmighty.lectures.firstday.board.comment.domain.CommentRepository;
import com.growmighty.lectures.firstday.board.comment.domain.CommentTargetType;
import com.growmighty.lectures.firstday.board.notice.domain.ProjectNoticeRepository;
import com.growmighty.lectures.firstday.board.review.domain.ReviewRepository;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private ProjectNoticeRepository projectNoticeRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private UserPort userPort;
    @Mock
    private ProjectPort projectPort;

    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(commentRepository, projectNoticeRepository, reviewRepository, userPort, projectPort);
    }

    @Test
    @DisplayName("PROJECT 대상은 ProjectPort로 존재를 확인한다")
    void register_projectTarget_success() {
        when(projectPort.existsProject(10L)).thenReturn(true);
        when(userPort.getUser(1L)).thenReturn(new UserSnapshot(1L, "홍길동"));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommentResult result = commentService.register(new RegisterCommentCommand(CommentTargetType.PROJECT, 10L, 1L, "내용"));

        assertThat(result.authorName()).isEqualTo("홍길동");
        verifyNoInteractions(projectNoticeRepository, reviewRepository);
    }

    @Test
    @DisplayName("PROJECT_NOTICE 대상은 ProjectNoticeRepository로 존재를 확인한다")
    void register_projectNoticeTarget_success() {
        when(projectNoticeRepository.existsVisibleById(20L)).thenReturn(true);
        when(userPort.getUser(1L)).thenReturn(new UserSnapshot(1L, "홍길동"));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        commentService.register(new RegisterCommentCommand(CommentTargetType.PROJECT_NOTICE, 20L, 1L, "내용"));

        verifyNoInteractions(projectPort, reviewRepository);
    }

    @Test
    @DisplayName("REVIEW 대상은 ReviewRepository로 존재를 확인한다")
    void register_reviewTarget_success() {
        when(reviewRepository.existsVisibleById(30L)).thenReturn(true);
        when(userPort.getUser(1L)).thenReturn(new UserSnapshot(1L, "홍길동"));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        commentService.register(new RegisterCommentCommand(CommentTargetType.REVIEW, 30L, 1L, "내용"));

        verifyNoInteractions(projectPort, projectNoticeRepository);
    }

    @Test
    @DisplayName("대상이 존재하지 않으면 user-service 조회 없이 바로 실패한다")
    void register_targetNotFound_failsBeforeUserLookup() {
        when(projectPort.existsProject(10L)).thenReturn(false);

        assertThatThrownBy(() -> commentService.register(new RegisterCommentCommand(CommentTargetType.PROJECT, 10L, 1L, "내용")))
            .isInstanceOf(EntityNotFoundException.class);

        verifyNoInteractions(userPort);
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("평탄한 목록을 부모-답글 구조로 그룹핑해 반환한다")
    void getByTarget_groupsRepliesUnderParent() {
        Comment root = Comment.create(CommentTargetType.PROJECT, 10L, 1L, "홍길동", "루트 댓글");
        ReflectionTestUtils.setField(root, "id", 100L);
        Comment reply = Comment.reply(root, 2L, "다른사람", "답글");
        ReflectionTestUtils.setField(reply, "id", 101L);
        when(commentRepository.findVisibleByTargetTypeAndTargetId(CommentTargetType.PROJECT, 10L))
            .thenReturn(List.of(root, reply));

        List<CommentResult> result = commentService.getByTarget(CommentTargetType.PROJECT, 10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(100L);
        assertThat(result.get(0).replies()).hasSize(1);
        assertThat(result.get(0).replies().get(0).id()).isEqualTo(101L);
    }
}