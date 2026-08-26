package com.growmighty.lectures.firstday.ai.tool.presentation.project;

import com.growmighty.lectures.firstday.ai.tool.feign.port.project.ProjectSearchPort;
import com.growmighty.lectures.firstday.ai.tool.feign.port.project.dto.ProjectSearchOutcome;
import com.growmighty.lectures.firstday.ai.tool.infrastructure.ToolInvocationRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class ProjectSearchTool {

    private final ProjectSearchPort projectSearchPort;

    @Tool(name = "search_projects", description = "프로젝트를 키워드/카테고리/상태로 검색한다. 사용자가 특정 프로젝트를 찾거나 추천을 요청할 때 사용.")
    public ProjectSearchOutcome searchProjects(
        @ToolParam(description = "검색 키워드(자연어 그대로 넘겨도 됨, 서버가 하이브리드 검색 처리)")
        String keyword,
        @ToolParam(description = "카테고리 ID. 사용자가 카테고리 이름만 언급했다면(ID를 모르면) " +
            "이 파라미터를 채우기 전에 먼저 list_project_categories를 호출해 이름↔ID를 확인할 것", required = false)
        Long categoryId,
        @ToolParam(description = "project-service ProjectStatus 중 비로그인/BACKER 조회에 노출되는 값만", required = false)
        ProjectSearchStatus status,
        @ToolParam(description = "정렬 기준. 사용자가 '최신순'/'마감임박순'/'펀딩 많이 된 순'처럼 " +
            "명시적으로 정렬을 요청했을 때만 지정하고, 그 외에는 반드시 비워둘 것 - " +
            "지정하는 순간 keyword 관련도 순위가 완전히 무시되고 이 기준으로만 정렬된다. " +
            "특히 이전 대화에서 언급된 프로젝트를 이번 턴에 재확인하는 검색에는 절대 쓰지 말 것.", required = false)
        ProjectSearchSort sort,
        ToolContext toolContext
    ) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("keyword는 비어 있을 수 없습니다.");
        }
        ToolInvocationRecorder recorder =
            (ToolInvocationRecorder) toolContext.getContext().get(ToolInvocationRecorder.TOOL_CONTEXT_KEY);
        recorder.recordToolUsed("search_projects");
        ProjectSearchOutcome outcome = projectSearchPort.search(
            keyword,
            categoryId,
            status != null ? status.name() : null,
            sort != null ? sort.name() : null,
            Set.of()
        );
        recorder.recordProjects(outcome.projects());
        return outcome;
    }

}
