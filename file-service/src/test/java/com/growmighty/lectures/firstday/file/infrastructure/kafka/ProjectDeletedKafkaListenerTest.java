package com.growmighty.lectures.firstday.file.infrastructure.kafka;

import com.growmighty.lectures.firstday.file.application.FileService;
import com.growmighty.lectures.firstday.file.domain.FileOwnerType;
import com.growmighty.lectures.firstday.file.infrastructure.kafka.dto.ProjectDeletedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ProjectDeletedKafkaListenerTest {

    private final FileService fileService = mock(FileService.class);
    private final ProjectDeletedKafkaListener listener = new ProjectDeletedKafkaListener(fileService);

    @Test
    @DisplayName("ProjectDeletedEvent 수신 시 해당 프로젝트의 파일들을 삭제한다")
    void onProjectDeleted_deletesFilesByOwner() {
        ProjectDeletedEvent event = ProjectDeletedEvent.of(100L);

        listener.onProjectDeleted(event);

        verify(fileService).deleteByOwner(FileOwnerType.PROJECT, 100L);
    }

    @Test
    @DisplayName("이벤트 페이로드가 null이면 삭제를 호출하지 않는다")
    void onProjectDeleted_nullPayload_ignores() {
        ProjectDeletedEvent event = new ProjectDeletedEvent(null, "ProjectDeleted", 1, null, null);

        listener.onProjectDeleted(event);

        verify(fileService, never()).deleteByOwner(any(), any());
    }
}
