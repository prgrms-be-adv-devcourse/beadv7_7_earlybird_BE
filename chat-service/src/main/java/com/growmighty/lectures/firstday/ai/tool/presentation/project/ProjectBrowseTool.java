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
public class ProjectBrowseTool {

    private final ProjectSearchPort projectSearchPort;

    @Tool(name = "browse_projects", description = "특정 키워드 없이 카테고리/상태/정렬만으로 프로젝트를 조회한다. " +
        "사용자가 검색어 없이 '지금 진행 중인 프로젝트 보여줘' 같이 조건만으로 전체 목록을 원할 때 사용 - " +
        "찾는 대상(제품명/취향 등)이 특정돼 있으면 search_projects를 대신 사용.")
    public List<ProjectSearchResult> browseProjects(
        @ToolParam(description = "카테고리 ID. 사용자가 카테고리 이름만 언급했다면(ID를 모르면) " +
            "이 파라미터를 채우기 전에 먼저 list_project_categories를 호출해 이름 ↔ ID 를 확인할 것", required = false)
        Long categoryId,
        @ToolParam(description = "project-service ProjectStatus 중 비로그인/BACKER 조회에 노출되는 값만", required = false)
        ProjectSearchStatus status,
        @ToolParam(description = "정렬 기준", required = false)
        ProjectSearchSort sort,
        ToolContext toolContext
    ) {
        ToolInvocationRecorder recorder =
            (ToolInvocationRecorder) toolContext.getContext().get(ToolInvocationRecorder.TOOL_CONTEXT_KEY);
        recorder.recordToolUsed("browse_projects");
        return projectSearchPort.search(
            null,
            categoryId,
            status != null ? status.name() : null,
            sort != null ? sort.name() : null
        );
    }
}
