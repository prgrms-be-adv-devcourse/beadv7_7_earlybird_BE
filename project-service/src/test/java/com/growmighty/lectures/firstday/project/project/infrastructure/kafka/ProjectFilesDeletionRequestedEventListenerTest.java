package com.growmighty.lectures.firstday.project.project.infrastructure.kafka;

import com.growmighty.lectures.firstday.project.project.application.port.FilePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProjectFilesDeletionRequestedEventListenerTest {

    private final FilePort filePort = mock(FilePort.class);
    private final ProjectFilesDeletionRequestedEventListener listener =
            new ProjectFilesDeletionRequestedEventListener(filePort);

    @Test
    @DisplayName("이벤트 수신 시 FilePort로 파일 정리를 위임한다")
    void onProjectFilesDeletionRequested_delegatesToFilePort() {
        listener.onProjectFilesDeletionRequested(new ProjectFilesDeletionRequestedEvent(1L));

        verify(filePort).deleteProjectFiles(1L);
    }
}
