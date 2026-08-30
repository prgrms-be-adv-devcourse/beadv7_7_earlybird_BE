package com.growmighty.lectures.firstday.file.infrastructure.kafka;

import com.growmighty.lectures.firstday.common.kafka.KafkaTopics;
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

    private final FileService fileService;

    /**
     * {@code spring.json.type.mapping}의 {@code projectDeleted} 별칭은 producer(project-service.yml의
     * 같은 이름 별칭)와 정확히 일치해야 하는 서비스 간 계약이다 — 두 서비스가 이 이벤트를 각자 자기
     * 패키지에 사본 record로 갖고 있어 FQCN이 다르기 때문이다. 별칭이 없으면 producer가 __TypeId__에
     * 실어 보내는 project-service FQCN을 여기서 로드하려다 "failed to resolve class name"으로 전량
     * DLT에 빠진다(ProjectDeletedEventDeserializationTest가 이 계약을 고정한다).
     * {@code value.default.type}은 타입 헤더가 아예 없는 메시지를 위한 폴백으로 남겨둔다.
     */
    @KafkaListener(
            topics = KafkaTopics.PROJECT_DELETED,
            groupId = "file-service",
            properties = {
                    "spring.json.type.mapping=projectDeleted:com.growmighty.lectures.firstday.file.infrastructure.kafka.dto.ProjectDeletedEvent",
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
