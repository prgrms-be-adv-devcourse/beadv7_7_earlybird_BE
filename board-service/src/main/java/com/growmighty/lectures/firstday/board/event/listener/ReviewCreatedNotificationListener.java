package com.growmighty.lectures.firstday.board.event.listener;

import com.growmighty.lectures.firstday.board.event.ReviewCreatedEvent;
import com.growmighty.lectures.firstday.board.event.port.EmailSender;
import com.growmighty.lectures.firstday.board.feign.port.ProjectPort;
import com.growmighty.lectures.firstday.board.feign.port.UserPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 리뷰 생성 시 해당 프로젝트 제작자에게 알림 메일을 보낸다.
 * AFTER_COMMIT: 리뷰 저장 트랜잭션이 커밋된 뒤에만 실행되고, 롤백되면 아예 실행되지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewCreatedNotificationListener {

    private final ProjectPort projectPort;
    private final UserPort userPort;
    private final EmailSender emailSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyCreator(ReviewCreatedEvent event) {
        try {
            Long creatorId = projectPort.getCreatorUserId(event.projectId());
            String creatorEmail = userPort.getUserEmail(creatorId);
            emailSender.send(
                creatorEmail,
                "회원님의 프로젝트에 새 리뷰가 등록되었습니다",
                "projectId=" + event.projectId() + " 프로젝트에 " + event.authorName() + "님이 리뷰를 남겼습니다. reviewId=" + event.reviewId());
        } catch (Exception e) {
            // 재시도는 하지 않는다 — 실패 지점만 명확히 남겨서, 추후 Kafka 전환 시 이 지점이 컨슈머 실패 처리로 대체된다.
            log.error("리뷰 생성 알림 메일 발송 실패. reviewId={}, projectId={}, 원인={}",
                event.reviewId(), event.projectId(), e.toString());
        }
    }
}