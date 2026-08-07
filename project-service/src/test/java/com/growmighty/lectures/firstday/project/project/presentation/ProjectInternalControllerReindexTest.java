package com.growmighty.lectures.firstday.project.project.presentation;

import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProjectInternalControllerReindexTest {

    private final ProjectService projectService = mock(ProjectService.class);
    private final ProjectInternalController controller = new ProjectInternalController(projectService);

    @Test
    @DisplayName("재색인 요청을 받으면 reindexAllProjects()를 호출한다")
    void reindex_callsReindexAllProjects() {
        controller.reindex();

        verify(projectService).reindexAllProjects();
    }
}
