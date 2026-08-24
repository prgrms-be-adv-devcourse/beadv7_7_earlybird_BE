package com.growmighty.lectures.firstday.ai.tool.presentation.project;

import com.growmighty.lectures.firstday.ai.tool.feign.port.project.ProjectSearchPort;
import com.growmighty.lectures.firstday.ai.tool.feign.port.project.dto.ProjectSearchResult;
import com.growmighty.lectures.firstday.ai.tool.infrastructure.ToolInvocationRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProjectSearchTool {

    private final ProjectSearchPort projectSearchPort;

    @Tool(name = "search_projects", description = "프로젝트를 키워드/카테고리/상태로 검색한다. 사용자가 특정 프로젝트를 찾거나 추천을 요청할 때 사용.")
    public List<ProjectSearchResult> searchProjects(
        @ToolParam(description = "검색 키워드(자연어 그대로 넘겨도 됨, 서버가 하이브리드 검색 처리)")
        String keyword,
        @ToolParam(description = "카테고리 ID. 사용자가 카테고리 이름만 언급했다면(ID를 모르면) " +
            "이 파라미터를 채우기 전에 먼저 list_project_categories를 호출해 이름↔ID를 확인할 것", required = false)
        Long categoryId,
        @ToolParam(description = "project-service ProjectStatus 중 비로그인/BACKER 조회에 노출되는 값만", required = false)
        ProjectSearchStatus status,
        @ToolParam(description = "정렬 기준", required = false)
        ProjectSearchSort sort,
        ToolContext toolContext
    ) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("keyword는 비어 있을 수 없습니다.");
        }
        ToolInvocationRecorder recorder =
            (ToolInvocationRecorder) toolContext.getContext().get(ToolInvocationRecorder.TOOL_CONTEXT_KEY);
        recorder.recordToolUsed("search_projects");
        return projectSearchPort.search(
            keyword,
            categoryId,
            status != null ? status.name() : null,
            sort != null ? sort.name() : null
        );
    }

}
