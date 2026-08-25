package com.growmighty.lectures.firstday.file.infrastructure.kafka;

import com.growmighty.lectures.firstday.file.application.FileService;
import com.growmighty.lectures.firstday.file.domain.FileOwnerType;
import com.growmighty.lectures.firstday.file.infrastructure.kafka.dto.ProjectDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * project-service의 ProjectDeletedEvent(project.deleted.v1)를 구독하여,
 * 해당 프로젝트에 속한 썸네일 등의 파일 메타데이터를 일괄 삭제한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectDeletedKafkaListener {

    private static final String TOPIC_PROJECT_DELETED = "project.deleted.v1";

    private final FileService fileService;

    @KafkaListener(
            topics = TOPIC_PROJECT_DELETED,
            groupId = "file-service",
            properties = {
                    "spring.json.value.default.type=com.growmighty.lectures.firstday.file.infrastructure.kafka.dto.ProjectDeletedEvent",
                    "spring.json.trusted.packages=*"
            }
    )
    public void onProjectDeleted(ProjectDeletedEvent event) {
        if (event == null || event.payload() == null || event.payload().projectId() == null) {
            log.warn("유효하지 않은 프로젝트 삭제 이벤트 무시. event={}", event);
            return;
        }
        Long projectId = event.payload().projectId();
        log.info("프로젝트 삭제 이벤트 수신 - 관련 파일 삭제 처리 시작. projectId={}", projectId);
        fileService.deleteByOwner(FileOwnerType.PROJECT, projectId);
        log.info("프로젝트 삭제 이벤트 처리 완료 (소유 파일 정리 완료). projectId={}", projectId);
    }
}
